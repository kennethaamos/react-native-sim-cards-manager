package com.reactnativesimcardsmanager;

import static android.content.Context.EUICC_SERVICE;

import android.os.Build;
import android.telephony.euicc.EuiccManager;
import com.facebook.react.bridge.*;
import android.app.PendingIntent;
import android.content.Intent;
import android.telephony.euicc.DownloadableSubscription;
import android.content.IntentFilter;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

import java.util.List;

@ReactModule(name = SimCardsManagerModule.NAME)
public class SimCardsManagerModule extends ReactContextBaseJavaModule {
  public static final String NAME = "SimCardsManager";
  private String ACTION_DOWNLOAD_SUBSCRIPTION = "download_subscription";
  private ReactContext mReactContext;

  @RequiresApi(api = Build.VERSION_CODES.P)
  private EuiccManager mgr;

  public SimCardsManagerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    mReactContext = reactContext;
    mgr = (EuiccManager) mReactContext.getSystemService(EUICC_SERVICE);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
  @ReactMethod
  public void getSimCards(Promise promise) {
    WritableArray simCardsList = new WritableNativeArray();

    TelephonyManager telManager = (TelephonyManager) mReactContext.getSystemService(Context.TELEPHONY_SERVICE);
    try {
      SubscriptionManager manager = (SubscriptionManager) mReactContext
          .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
        int activeSubscriptionInfoCount = manager.getActiveSubscriptionInfoCount();
        int activeSubscriptionInfoCountMax = manager.getActiveSubscriptionInfoCountMax();

        List<SubscriptionInfo> subscriptionInfos = manager.getActiveSubscriptionInfoList();

        for (SubscriptionInfo subInfo : subscriptionInfos) {
          WritableMap simCard = Arguments.createMap();

          CharSequence carrierName = subInfo.getCarrierName();
          String countryIso = subInfo.getCountryIso();
          int dataRoaming = subInfo.getDataRoaming(); // 1 is enabled ; 0 is disabled
          CharSequence displayName = subInfo.getDisplayName();
          String iccId = subInfo.getIccId();
          int mcc = subInfo.getMcc();
          int mnc = subInfo.getMnc();
          String number = subInfo.getNumber();
          int simSlotIndex = subInfo.getSimSlotIndex();
          int subscriptionId = subInfo.getSubscriptionId();
          int networkRoaming = telManager.isNetworkRoaming() ? 1 : 0;
          String deviceId = null;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            deviceId = telManager.getDeviceId(simSlotIndex);
          }

          simCard.putString("carrierName", carrierName.toString());
          simCard.putString("displayName", displayName.toString());
          simCard.putString("isoCountryCode", countryIso);
          simCard.putInt("mobileCountryCode", mcc);
          simCard.putInt("mobileNetworkCode", mnc);
          simCard.putInt("isNetworkRoaming", networkRoaming);
          simCard.putInt("isDataRoaming", dataRoaming);
          simCard.putInt("simSlotIndex", simSlotIndex);
          simCard.putString("phoneNumber", number);
          simCard.putString("deviceId", deviceId);
          simCard.putString("simSerialNumber", iccId);
          simCard.putInt("subscriptionId", subscriptionId);

          simCardsList.pushMap(simCard);
        }
      } else {
        promise.reject("0", "This functionality is not supported before Android 5.1 (22)");
      }
    } catch (Exception e) {
      promise.reject("1", "Something goes wrong to fetch simcards:" + e.getLocalizedMessage());
    }
    promise.resolve(simCardsList);
  }

  @RequiresApi(api = Build.VERSION_CODES.P)
  @ReactMethod
  public void isEsimSupported(Promise promise) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mgr != null) {
      promise.resolve(mgr.isEnabled());
    } else {
      promise.resolve(false);
    }
    return;
  }

  @RequiresApi(api = Build.VERSION_CODES.P)
  @ReactMethod
  public void setupEsim(ReadableMap config, Promise promise) {

    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
      promise.reject("0", "EuiccManager is not available or before Android 9 (API 28)");
    }
    
    if (mgr == null) {
      promise.reject("1", "The device doesn't support a cellular plan (EuiccManager is not available)");
      return;
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {

      @Override
      public void onReceive(Context context, Intent intent) {
        if (!ACTION_DOWNLOAD_SUBSCRIPTION.equals(intent.getAction())) {
          promise.reject("3",
              "Can't setup eSim du to wrong Intent:" + intent.getAction()+" instead of "+ACTION_DOWNLOAD_SUBSCRIPTION);
          return;
        }

        int resultCode = getResultCode();
        if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR && mgr != null) {
          try {
            // Resolvable error, attempt to resolve it by a user action
            // FIXME: review logic of resolve functions
            promise.resolve(3);
            PendingIntent callbackIntent = PendingIntent.getBroadcast(mReactContext, 3,
                new Intent(ACTION_DOWNLOAD_SUBSCRIPTION), PendingIntent.FLAG_ONE_SHOT);
            mgr.startResolutionActivity(mReactContext.getCurrentActivity(), 3, intent, callbackIntent);
          } catch (Exception e) {
            promise.reject("3", "EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR - Can't setup eSim du to Activity error" + e.getLocalizedMessage());
          }
        } else if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK) {
          promise.resolve(true);
        } else if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR) {
          // Embedded Subscription Error
          promise.reject("3",
              "EMBEDDED_SUBSCRIPTION_RESULT_ERROR - Can't add an Esim subscription");
        } else {
          // Unknown Error
          promise.reject("3", "Can't add an Esim subscription due to unknown error, resultCode is:" + String.valueOf(resultCode));
        }
      }
    };

    mReactContext.registerReceiver(
        receiver,
        new IntentFilter(ACTION_DOWNLOAD_SUBSCRIPTION),
        null,
        null);

    DownloadableSubscription sub = DownloadableSubscription.forActivationCode(
        /* Passed from react side */
        config.getString("confirmationCode"));

    PendingIntent callbackIntent = PendingIntent.getBroadcast(
        mReactContext,
        0,
        new Intent(ACTION_DOWNLOAD_SUBSCRIPTION),
        PendingIntent.FLAG_UPDATE_CURRENT);

    mgr.downloadSubscription(sub, true, callbackIntent);
  }
}
