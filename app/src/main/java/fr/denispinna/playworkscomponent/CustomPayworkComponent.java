package fr.denispinna.playworkscomponent;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import io.mpos.Mpos;
import io.mpos.accessories.AccessoryFamily;
import io.mpos.accessories.parameters.AccessoryParameters;
import io.mpos.android.internal.AndroidImageHelper;
import io.mpos.paymentdetails.ApplicationInformation;
import io.mpos.paymentdetails.DccInformation;
import io.mpos.provider.ProviderMode;
import io.mpos.provider.listener.TransactionListener;
import io.mpos.transactionprovider.*;
import io.mpos.transactions.Currency;
import io.mpos.transactions.Transaction;
import io.mpos.transactions.parameters.TransactionParameters;
import io.mpos.transactions.receipts.Receipt;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CustomPayworkComponent {
    private static final String TAG = "CustomPayworkComponent";

    private Context context;
    private String merchantIdentifier;
    private String merchantSecretKey;
    private HashMap<String, TransactionProcess> currentTransactions = new HashMap<>();

    /**
     * This method start a transaction and return the id
     * <p>
     * exemple of params:
     * transactionValue = "5.00"
     * transactionCurrency = Currency.EUR
     * transactionSubject = "Bouquet of Flowers"
     * transactionCustomIdentifier = "yourReferenceForTheTransaction"
     */
    public String startTransaction(
            String transactionValue,
            Currency transactionCurrency,
            String transactionSubject,
            String transactionCustomIdentifier,
            ProviderMode providerMode,
            final TransactionProcessWithRegistrationListener listener) {
        TransactionProvider transactionProvider = Mpos.createTransactionProvider(
                context,
                providerMode,
                merchantIdentifier,
                merchantSecretKey
        );

        // For starting transaction in mocked mode use fallowing provider:
//        TransactionProvider transactionProvider = Mpos . createTransactionProvider (this,
//        ProviderMode.MOCK,
//        "merchantIdentifier",
//        "merchantSecretKey");


        /* When using the Bluetooth Miura, use the following parameters: */
        AccessoryParameters accessoryParameters = new AccessoryParameters.Builder(AccessoryFamily.MIURA_MPI)
                .bluetooth()
                .build();




    /* When using Verifone readers via WiFi or Ethernet, use the following parameters:
    AccessoryParameters accessoryParameters = new AccessoryParameters.Builder(AccessoryFamily.VERIFONE_VIPA)
                                                                     .tcp("192.168.254.123", 16107)
                                                                     .build();
    */

        TransactionParameters transactionParameters = new TransactionParameters.Builder()
                .charge(new BigDecimal(transactionValue), transactionCurrency)
                .subject(transactionSubject)
                .customIdentifier(transactionCustomIdentifier)
                .build();

        TransactionProcess transaction = transactionProvider.startTransaction(transactionParameters, accessoryParameters,
                new TransactionProcessWithRegistrationListener() {

                    @Override
                    public void onRegistered(TransactionProcess process,
                                             Transaction transaction) {
                        Log.d("mpos", "transaction identifier is: " + transaction.getIdentifier() + ". Store it in your backend so that you can always query its status.");
                        listener.onRegistered(process, transaction);
                    }

                    @Override
                    public void onStatusChanged(TransactionProcess process ,
                                                Transaction transaction,
                                                TransactionProcessDetails processDetails) {
                        Log.d("mpos", "status changed: " + Arrays.toString(processDetails.getInformation()));
                        listener.onStatusChanged(process, transaction, processDetails);
                    }

                    @Override
                    public void onCustomerSignatureRequired(TransactionProcess process,
                                                            Transaction transaction) {
                        // in a live app, this image comes from your signature screen
                        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
                        Bitmap bm = Bitmap.createBitmap(1, 1, conf);
                        byte[] signature = AndroidImageHelper.byteArrayFromBitmap(bm);
                        process.continueWithCustomerSignature(signature, true);
                        listener.onCustomerSignatureRequired(process, transaction);
                    }

                    @Override
                    public void onCustomerVerificationRequired(TransactionProcess process,
                                                               Transaction transaction) {
                        // always return false here
                        process.continueWithCustomerIdentityVerified(false);
                        listener.onCustomerVerificationRequired(process, transaction);
                    }

                    @Override
                    public void onApplicationSelectionRequired(TransactionProcess process,
                                                               Transaction transaction,
                                                               List<ApplicationInformation>
                                                                       applicationInformation) {
                        // This happens only for readers that don't support application selection on their screen
                        process.continueWithSelectedApplication(applicationInformation.get(0));
                        listener.onApplicationSelectionRequired(process, transaction, applicationInformation);
                    }

                    @Override
                    public void onDccSelectionRequired(TransactionProcess transactionProcess,
                                                       Transaction transaction,
                                                       DccInformation dccInformation) {

                        // This comes up if the DCC selection cannot be done on the terminal itself
                        transactionProcess.continueDccSelectionWithOriginalAmount();
                        listener.onDccSelectionRequired(transactionProcess, transaction, dccInformation);
                    }

                    @Override
                    public void onCompleted(TransactionProcess process,
                                            Transaction transaction,
                                            TransactionProcessDetails processDetails) {
                        Log.d("mpos", "completed");
                        if (processDetails.getState() == TransactionProcessDetailsState.APPROVED) {
                            // print the merchant receipt
                            Receipt merchantReceipt = transaction.getMerchantReceipt();

                            // print a signature line if required
                            if(merchantReceipt.isSignatureLineRequired()) {
                                System.out.println("");
                                System.out.println("");
                                System.out.println("");
                                System.out.println("------ PLEASE SIGN HERE ------");
                            }

                            // ask the merchant, whether the shopper wants to have a receipt
                            Receipt customerReceipt = transaction.getCustomerReceipt();

                            // and close the checkout UI
                        } else {
                            // Allow your merchant to try another transaction
                        }
                        listener.onCompleted(process, transaction, processDetails);
                    }
                });

        String transactionId = transaction.getTransaction().getIdentifier();
        currentTransactions.put(transactionId, transaction);
        return transactionId;
    }

    public void cancelTransaction(String identifier) {
        try {
            currentTransactions.get(identifier).requestAbort();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
