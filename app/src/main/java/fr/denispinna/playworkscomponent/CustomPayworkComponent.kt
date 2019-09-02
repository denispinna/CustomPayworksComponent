package fr.denispinna.playworkscomponent

import android.content.Context
import io.mpos.transactionprovider.TransactionProcessDetails
import io.mpos.transactionprovider.TransactionProcess
import io.mpos.paymentdetails.DccInformation
import io.mpos.paymentdetails.ApplicationInformation
import io.mpos.android.internal.AndroidImageHelper
import android.graphics.Bitmap
import android.util.Log
import io.mpos.transactionprovider.TransactionProcessWithRegistrationListener
import io.mpos.transactions.parameters.TransactionParameters
import io.mpos.accessories.AccessoryFamily
import io.mpos.accessories.parameters.AccessoryParameters
import io.mpos.provider.ProviderMode
import io.mpos.Mpos
import io.mpos.transactions.Transaction
import java.math.BigDecimal
import io.mpos.transactions.Currency
import java.util.*
import kotlin.collections.HashMap


class CustomPayworkComponent(
    private val context: Context,
    private val merchantIdentifier: String,
    private val merchantSecretKey: String
) {
    private val currentTransactions = HashMap<String, TransactionProcess>()

    /**
     * This method start a transaction and return the id
     *
     * exemple of params:
     * transactionValue = "5.00"
     * transactionCurrency = Currency.EUR
     * transactionSubject = "Bouquet of Flowers"
     * transactionCustomIdentifier = "yourReferenceForTheTransaction"
     */
    fun transaction(
        transactionValue: String,
        transactionCurrency: Currency,
        transactionSubject: String,
        transactionCustomIdentifier: String,
        onTransactionCompleted:
            (process: TransactionProcess,
             transaction: Transaction,
             processDetails: TransactionProcessDetails) -> Unit,
        onTransactionStatusChanged:
            (process: TransactionProcess,
             transaction: Transaction,
             processDetails: TransactionProcessDetails) -> Unit) : String{
        val transactionProvider = Mpos.createTransactionProvider(
            context,
            ProviderMode.TEST,
            merchantIdentifier,
            merchantSecretKey
        )

        // For starting transaction in mocked mode use fallowing provider:
//        TransactionProvider transactionProvider = Mpos . createTransactionProvider (this,
//        ProviderMode.MOCK,
//        "merchantIdentifier",
//        "merchantSecretKey");


        /* When using the Bluetooth Miura, use the following parameters: */
        val accessoryParameters = AccessoryParameters.Builder(AccessoryFamily.MIURA_MPI)
            .bluetooth()
            .build()


        /* When using Verifone readers via WiFi or Ethernet, use the following parameters:
    AccessoryParameters accessoryParameters = new AccessoryParameters.Builder(AccessoryFamily.VERIFONE_VIPA)
                                                                     .tcp("192.168.254.123", 16107)
                                                                     .build();
    */

        val transactionParameters = TransactionParameters.Builder()
            .charge(BigDecimal(transactionValue), transactionCurrency)
            .subject(transactionSubject)
            .customIdentifier(transactionCustomIdentifier)
            .build()

        val transaction = transactionProvider.startTransaction(transactionParameters, accessoryParameters,
            object : TransactionProcessWithRegistrationListener {

                override fun onRegistered(
                    process: TransactionProcess,
                    transaction: Transaction
                ) {
                    Log.d(
                        "mpos",
                        "transaction identifier is: " + transaction.identifier + ". Store it in your backend so that you can always query its status."
                    )
                }

                override fun onStatusChanged(
                    process: TransactionProcess,
                    transaction: Transaction,
                    processDetails: TransactionProcessDetails
                ) {
                    Log.d("mpos", "status changed: " + Arrays.toString(processDetails.information))
                    onTransactionStatusChanged(process, transaction, processDetails)
                }

                override fun onCustomerSignatureRequired(
                    process: TransactionProcess,
                    transaction: Transaction
                ) {
                    // in a live app, this image comes from your signature screen
                    val conf = Bitmap.Config.ARGB_8888
                    val bm = Bitmap.createBitmap(1, 1, conf)
                    val signature = AndroidImageHelper.byteArrayFromBitmap(bm)
                    process.continueWithCustomerSignature(signature, true)
                }

                override fun onCustomerVerificationRequired(
                    process: TransactionProcess,
                    transaction: Transaction
                ) {
                    // always return false here
                    process.continueWithCustomerIdentityVerified(false)
                }

                override fun onApplicationSelectionRequired(
                    process: TransactionProcess,
                    transaction: Transaction,
                    applicationInformation: List<ApplicationInformation>
                ) {
                    // This happens only for readers that don't support application selection on their screen
                    process.continueWithSelectedApplication(applicationInformation[0])
                }

                override fun onDccSelectionRequired(
                    transactionProcess: TransactionProcess,
                    transaction: Transaction,
                    dccInformation: DccInformation
                ) {
                    // This comes up if the DCC selection cannot be done on the terminal itself
                    transactionProcess.continueDccSelectionWithOriginalAmount()

                }

                override fun onCompleted(
                    process: TransactionProcess,
                    transaction: Transaction,
                    processDetails: TransactionProcessDetails
                ) {
                    Log.d("mpos", "completed")

                    /**
                     * We first remove the transaction from the list
                     */
                    currentTransactions.remove(transaction.identifier)
                    onTransactionCompleted(process, transaction, processDetails)
                }
            })
        val transactionId = transaction.transaction.identifier
        currentTransactions[transactionId] = transaction

        return transactionId
    }

    fun cancelTransaction(identifier: String) {
        currentTransactions[identifier]?.requestAbort()
    }
}