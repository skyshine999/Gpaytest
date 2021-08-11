
package com.magtek.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wallet.*
import com.google.android.material.textfield.TextInputEditText
import com.magtek.gpaytest.R
import org.json.JSONException
import org.json.JSONObject


class CheckoutActivity : Activity() {

    private lateinit var paymentsClient: PaymentsClient
    private val LOAD_PAYMENT_DATA_REQUEST_CODE = 991
    private lateinit var googlePayButton:RelativeLayout
    private lateinit var editText:TextInputEditText
    private lateinit var responseText:TextView
    private lateinit var responseLayout: RelativeLayout
    private lateinit var shareBtn:Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        // Initialize a Google Pay API client for an environment suitable for testing.
        // It's recommended to create the PaymentsClient object inside of the onCreate method.
        paymentsClient = PaymentsUtil.createPaymentsClient(this)
        googlePayButton = findViewById(R.id.googlePayButton)
        editText = findViewById(R.id.input_text)
        responseText= findViewById(R.id.response_txt)
        responseText.setMovementMethod(ScrollingMovementMethod())
        responseLayout = findViewById(R.id.response_layout)
        shareBtn = findViewById(R.id.share_btn)
        possiblyShowGooglePayButton()

        googlePayButton.setOnClickListener { requestPayment() }
        shareBtn.setOnClickListener { shareResponse()}
    }

    /**
     * Determine the viewer's ability to pay with a payment method supported by your app and display a
     * Google Pay payment button.
     *
     * @see [](https://developers.google.com/android/reference/com/google/android/gms/wallet/PaymentsClient.html.isReadyToPay
    ) */
    private fun possiblyShowGooglePayButton() {

        val isReadyToPayJson = PaymentsUtil.isReadyToPayRequest() ?: return
        val request = IsReadyToPayRequest.fromJson(isReadyToPayJson.toString()) ?: return

        // The call to isReadyToPay is asynchronous and returns a Task. We need to provide an
        // OnCompleteListener to be triggered when the result of the call is known.
        val task = paymentsClient.isReadyToPay(request)
        task.addOnCompleteListener { completedTask ->
            try {
                completedTask.getResult(ApiException::class.java)?.let(::setGooglePayAvailable)
            } catch (exception: ApiException) {
                // Process error
                Log.w("isReadyToPay failed", exception)
            }
        }
    }

    private fun shareResponse(){
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "text/plain"
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Token Response")
        sharingIntent.putExtra(Intent.EXTRA_TEXT, responseText.text.toString())
        startActivity(sharingIntent)
    }


    /**
     * If isReadyToPay returned `true`, show the button and hide the "checking" text. Otherwise,
     * notify the user that Google Pay is not available. Please adjust to fit in with your current
     * user flow. You are not required to explicitly let the user know if isReadyToPay returns `false`.
     *
     * @param available isReadyToPay API response.
     */
    private fun setGooglePayAvailable(available: Boolean) {
        if (available) {
            googlePayButton.visibility = View.VISIBLE
        } else {
            Toast.makeText(
                    this,
                    "Unfortunately, Google Pay is not available on this device",
                    Toast.LENGTH_LONG).show();
        }
    }
    
    private fun requestPayment() {

        googlePayButton.isClickable = false
        var amount = editText.text
        val paymentDataRequestJson = PaymentsUtil.getPaymentDataRequest(amount.toString())
        if (paymentDataRequestJson == null) {
            Log.e("RequestPayment", "Can't fetch payment data request")
            return
        }
        Log.e("gpay",paymentDataRequestJson.toString())
        val request = PaymentDataRequest.fromJson(paymentDataRequestJson.toString())

        // Since loadPaymentData may show the UI asking the user to select a payment method, we use
        // AutoResolveHelper to wait for the user interacting with it. Once completed,
        // onActivityResult will be called with the result.
        if (request != null) {
            AutoResolveHelper.resolveTask(
                    paymentsClient.loadPaymentData(request), this, LOAD_PAYMENT_DATA_REQUEST_CODE)
        }
    }

    /**
     * Handle a resolved activity from the Google Pay payment sheet.
     *
     * @param requestCode Request code originally supplied to AutoResolveHelper in requestPayment().
     * @param resultCode Result code returned by the Google Pay API.
     * @param data Intent from the Google Pay API containing payment or error data.
     * @see [Getting a result
     * from an Activity](https://developer.android.com/training/basics/intents/result)
     */
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            // value passed in AutoResolveHelper
            LOAD_PAYMENT_DATA_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK ->
                        data?.let { intent ->
                            PaymentData.getFromIntent(intent)?.let(::handlePaymentSuccess)
                        }

                    Activity.RESULT_CANCELED -> {
                        // Nothing to do here normally - the user simply cancelled without selecting a
                        // payment method.
                    }

                    AutoResolveHelper.RESULT_ERROR -> {
                        AutoResolveHelper.getStatusFromIntent(data)?.let {
                            handleError(it.statusCode)
                        }
                    }
                }
                // Re-enables the Google Pay payment button.
                googlePayButton.isClickable = true
            }
        }
    }

    /**
     * PaymentData response object contains the payment information, as well as any additional
     * requested information, such as billing and shipping address.
     *
     * @param paymentData A response object returned by Google after a payer approves payment.
     * @see [Payment
     * Data](https://developers.google.com/pay/api/android/reference/object.PaymentData)
     */
    private fun handlePaymentSuccess(paymentData: PaymentData) {
        val paymentInformation = paymentData.toJson() ?: return

        try {
            // Token will be null if PaymentDataRequest was not constructed using fromJson(String).
            val paymentMethodData = JSONObject(paymentInformation).getJSONObject("paymentMethodData")

            // If the gateway is set to "example", no payment information is returned - instead, the
            // token will only consist of "examplePaymentMethodToken".
            Log.e("gpay",paymentMethodData.toString())
            responseText.text = paymentMethodData.toString()
            responseLayout.visibility = View.VISIBLE
            if (paymentMethodData
                            .getJSONObject("tokenizationData")
                            .getString("type") == "PAYMENT_GATEWAY" && paymentMethodData
                            .getJSONObject("tokenizationData")
                            .getString("token") == "examplePaymentMethodToken") {

                AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage("Gateway name set to \"example\" - please modify " +
                                "Constants.java and replace it with your own gateway.")
                        .setPositiveButton("OK", null)
                        .create()
                        .show()
            }

            val billingName = paymentMethodData.getJSONObject("info")
                    .getJSONObject("billingAddress").getString("name")
            Log.d("BillingName", billingName)

            // Logging token string.
            Log.d("GooglePaymentToken", paymentMethodData
                    .getJSONObject("tokenizationData")
                    .getString("token"))

        } catch (e: JSONException) {
            Log.e("handlePaymentSuccess", "Error: " + e.toString())
        }

    }

    /**
     * At this stage, the user has already seen a popup informing them an error occurred. Normally,
     * only logging is required.
     *
     * @param statusCode will hold the value of any constant from CommonStatusCode or one of the
     * WalletConstants.ERROR_CODE_* constants.
     * @see [
     * Wallet Constants Library](https://developers.google.com/android/reference/com/google/android/gms/wallet/WalletConstants.constant-summary)
     */
    private fun handleError(statusCode: Int) {
        Log.w("loadPaymentData failed", String.format("Error code: %d", statusCode))
    }

}
