package com.banano.kaliumwallet.ui.settings;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.banano.kaliumwallet.ui.common.SwipeDismissTouchListener;
import com.github.ajalt.reprint.core.AuthenticationFailureReason;
import com.github.ajalt.reprint.core.Reprint;
import com.hwangjr.rxbus.annotation.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import com.banano.kaliumwallet.BuildConfig;
import com.banano.kaliumwallet.R;
import com.banano.kaliumwallet.bus.CreatePin;
import com.banano.kaliumwallet.bus.Logout;
import com.banano.kaliumwallet.bus.PinComplete;
import com.banano.kaliumwallet.bus.RxBus;
import com.banano.kaliumwallet.databinding.FragmentSettingsBinding;
import com.banano.kaliumwallet.model.AvailableCurrency;
import com.banano.kaliumwallet.model.Credentials;
import com.banano.kaliumwallet.model.StringWithTag;
import com.banano.kaliumwallet.network.AccountService;
import com.banano.kaliumwallet.ui.common.ActivityWithComponent;
import com.banano.kaliumwallet.ui.common.BaseDialogFragment;
import com.banano.kaliumwallet.ui.common.WindowControl;
import com.banano.kaliumwallet.util.SharedPreferencesUtil;
import io.realm.Realm;

/**
 * Settings main screen
 */
public class SettingsDialogFragment extends BaseDialogFragment {
    private FragmentSettingsBinding binding;
    public static String TAG = SettingsDialogFragment.class.getSimpleName();
    private AlertDialog fingerprintDialog;
    private boolean backupSeedPinEntered = false;

    @Inject
    SharedPreferencesUtil sharedPreferencesUtil;

    @Inject
    Realm realm;

    @Inject
    AccountService accountService;

    /**
     * Create new instance of the dialog fragment (handy pattern if any data needs to be passed to it)
     *
     * @return New instance of SettingsDialogFragment
     */
    public static SettingsDialogFragment newInstance() {
        Bundle args = new Bundle();
        SettingsDialogFragment fragment = new SettingsDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.AppTheme_Modal_WindowLeft);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // inject
        if (getActivity() instanceof ActivityWithComponent) {
            ((ActivityWithComponent) getActivity()).getActivityComponent().inject(this);
        }
        backupSeedPinEntered = false;

        // TODO fix this, this doesn't work well after onResume
        /*
        if (getDialog() != null) {
            getDialog().setCanceledOnTouchOutside(true);
        }*/

        // inflate the view
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_settings, container, false);
        view = binding.getRoot();
        binding.setHandlers(new ClickHandlers());
        binding.setVersion(getString(R.string.version_display, BuildConfig.VERSION_NAME));

        // Restrict width
        Window window = getDialog().getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        Point size = new Point();

        Display display = window.getWindowManager().getDefaultDisplay();
        display.getSize(size);

        int width = size.x;

        window.setLayout((int) (width * 0.9), WindowManager.LayoutParams.MATCH_PARENT);
        window.setGravity(Gravity.LEFT);

        // Shadow
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams windowParams = window.getAttributes();
        windowParams.dimAmount = 0.60f;
        windowParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(windowParams);

        // Swipe left to dismiss
        getDialog().getWindow().getDecorView().setOnTouchListener(new SwipeDismissTouchListener(getDialog().getWindow().getDecorView(),
                null, new SwipeDismissTouchListener.DismissCallbacks() {
            @Override
            public boolean canDismiss(Object token) {
                return true;
            }

            @Override
            public void onDismiss(View view, Object token) {
                dismiss();
            }

            @Override
            public void onTap(View view) { }
        }, SwipeDismissTouchListener.LEFT_TO_RIGHT));

        // subscribe to bus
        RxBus.get().register(this);

        // set up currency spinner
        List<StringWithTag> availableCurrencies = getAllCurrencies();
        ArrayAdapter<StringWithTag> spinnerArrayAdapter = new ArrayAdapter<>(getContext(),
                R.layout.view_spinner_item,
                availableCurrencies
        );
        spinnerArrayAdapter.setDropDownViewResource(R.layout.view_spinner_dropdown_item);
        binding.settingsCurrencySpinner.setVisibility(View.VISIBLE);
        binding.settingsCurrencySpinner.setAdapter(spinnerArrayAdapter);
        binding.settingsCurrencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                // save local currency to shared preferences
                StringWithTag swt = (StringWithTag) adapterView.getItemAtPosition(i);
                AvailableCurrency key = (AvailableCurrency) swt.tag;
                if (key != null) {
                    sharedPreferencesUtil.setLocalCurrency(key);
                    final HashMap<String, String> customData = new HashMap<>();
                    customData.put("currency", key.toString());
                    // update currency amounts
                    accountService.requestSubscribe();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        // set selected item with value saved in shared preferences
        binding.settingsCurrencySpinner.setSelection(getIndexOf(sharedPreferencesUtil.getLocalCurrency(), availableCurrencies));

/*
        Credentials credentials = realm.where(Credentials.class).findFirst();
        if (credentials != null) {
            binding.settingsShowNewSeed.setVisibility(credentials.getNewlyGeneratedSeed() != null ? View.VISIBLE : View.GONE);
        }
*/
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // unregister from bus
        RxBus.get().unregister(this);
    }

    /**
     * Pin entered correctly
     *
     * @param pinComplete PinComplete object
     */
    @Subscribe
    public void receivePinComplete(PinComplete pinComplete) {
        if (backupSeedPinEntered) {
            showBackupSeedDialog();
            backupSeedPinEntered = false;
        }
    }

    @Subscribe
    public void receiveCreatePin(CreatePin pinComplete) {
        realm.beginTransaction();
        Credentials credentials = realm.where(Credentials.class).findFirst();
        if (credentials != null) {
            credentials.setPin(pinComplete.getPin());
        }
        realm.commitTransaction();
        if (backupSeedPinEntered) {
            showBackupSeedDialog();
            backupSeedPinEntered = false;
        }
    }

    /**
     * Get list of all of the available currencies
     *
     * @return Lost of all currencies the app supports
     */
    private List<StringWithTag> getAllCurrencies() {
        List<StringWithTag> itemList = new ArrayList<>();
        for (AvailableCurrency currency : AvailableCurrency.values()) {
            itemList.add(new StringWithTag(currency.getFullDisplayName(), currency));
        }
        return itemList;
    }

    /**
     * Get Index of a particular currency
     *
     * @return Index of a particular currency in the spinner
     */
    private int getIndexOf(AvailableCurrency currency, List<StringWithTag> availableCurrencies) {
        int i = 0;
        for (StringWithTag availableCurrency : availableCurrencies) {
            if (availableCurrency.tag.equals(currency)) {
                return i;
            }
            i++;
        }
        return 0;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHANGE_RESULT) {
            if (resultCode == CHANGE_COMPLETE) {
                Toast.makeText(getContext(),
                        getString(R.string.change_representative_success),
                        Toast.LENGTH_SHORT)
                        .show();
            } else if (resultCode == CHANGE_FAILED) {
                Toast.makeText(getContext(),
                        getString(R.string.change_representative_error),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    private void showChangeRepDialog() {
        // show change rep dialog
        ChangeRepDialogFragment dialog = ChangeRepDialogFragment.newInstance();
        dialog.setTargetFragment(this, CHANGE_RESULT);
        dialog.show(((WindowControl) getActivity()).getFragmentUtility().getFragmentManager(),
                ChangeRepDialogFragment.TAG);
        ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().executePendingTransactions();
    }

    private void showBackupSeedDialog() {
        // show backup seed dialog
        BackupSeedDialogFragment dialog = BackupSeedDialogFragment.newInstance();
        dialog.show(((WindowControl) getActivity()).getFragmentUtility().getFragmentManager(),
                BackupSeedDialogFragment.TAG);
        ((WindowControl) getActivity()).getFragmentUtility().getFragmentManager().executePendingTransactions();
    }

    public class ClickHandlers {
        public void onClickCurrency(View view) {
            binding.settingsCurrencySpinner.performClick();
        }

        public void onClickChange(View view) {
            if (getActivity() instanceof WindowControl) {
                showChangeRepDialog();
            }
        }

        public void onClickBackupSeed(View view) {
            Credentials credentials = realm.where(Credentials.class).findFirst();

            if (Reprint.isHardwarePresent() && Reprint.hasFingerprintRegistered()) {
                // show fingerprint dialog
                LayoutInflater factory = LayoutInflater.from(getContext());
                @SuppressLint("InflateParams") final View viewFingerprint = factory.inflate(R.layout.view_fingerprint, null);
                showFingerprintDialog(viewFingerprint);
                com.github.ajalt.reprint.rxjava2.RxReprint.authenticate()
                        .subscribe(result -> {
                            switch (result.status) {
                                case SUCCESS:
                                    fingerprintDialog.hide();
                                    showBackupSeedDialog();
                                    break;
                                case NONFATAL_FAILURE:
                                    showFingerprintError(result.failureReason, result.errorMessage, viewFingerprint);
                                    break;
                                case FATAL_FAILURE:
                                    showFingerprintError(result.failureReason, result.errorMessage, viewFingerprint);
                                    break;
                            }
                        });
            } else if (credentials != null && credentials.getPin() != null) {
                backupSeedPinEntered = true;
                showPinScreen(getString(R.string.settings_pin_title));
            } else if (credentials != null && credentials.getPin() == null) {
                backupSeedPinEntered = true;
                showCreatePinScreen();
            }
        }

        public void onClickShare(View view) {
            String playStoreUrl = "https://play.google.com/store/apps/details?id=" + getActivity().getPackageName();

            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.share_extra) + "\n" + playStoreUrl);
            startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_title)));
        }

        public void onClickLogOut(View view) {
            if (getActivity() instanceof WindowControl) {

                // show the logout are-you-sure dialog
                AlertDialog.Builder builder;
                SpannableString title = new SpannableString(getString(R.string.settings_logout_alert_title));
                title.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                SpannableString positive = new SpannableString(getString(R.string.settings_logout_alert_confirm_cta));
                positive.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, positive.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                SpannableString negative = new SpannableString(getString(R.string.settings_logout_alert_cancel_cta));
                negative.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, negative.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                // Sub dialog
                SpannableString warningTitle = new SpannableString(getString(R.string.settings_logout_warning_title));
                warningTitle.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, warningTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                SpannableString yes = new SpannableString(getString(R.string.settings_logout_warning_confirm));
                yes.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, yes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                int style = android.os.Build.VERSION.SDK_INT >= 21 ? R.style.AlertDialogCustom : android.R.style.Theme_Holo_Dialog;
                builder = new AlertDialog.Builder(getContext(), style);
                builder.setTitle(title)
                        .setMessage(R.string.settings_logout_alert_message)
                        .setPositiveButton(positive, (dialog, which) -> {
                            AlertDialog.Builder builderWarning;
                            builderWarning = new AlertDialog.Builder(getContext(), style);
                            builderWarning.setTitle(warningTitle)
                                    .setMessage(R.string.settings_logout_warning_message)
                                    .setPositiveButton(yes, (dialogWarn, whichWarn) -> {
                                        RxBus.get().post(new Logout());
                                        dismiss();
                                    })
                                    .setNegativeButton(negative, (dialogWarn, whichWarn) -> {
                                        // do nothing which dismisses the dialog
                                    })
                                    .show();
                        })
                        .setNegativeButton(negative, (dialog, which) -> {
                            // do nothing which dismisses the dialog
                        })
                        .show();
            }
        }
    }

    private void showFingerprintDialog(View view) {
        int style = android.os.Build.VERSION.SDK_INT >= 21 ? R.style.AlertDialogCustom : android.R.style.Theme_Holo_Dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), style);
        builder.setMessage(getString(R.string.settings_fingerprint_title));
        builder.setView(view);
        SpannableString negativeText = new SpannableString(getString(android.R.string.cancel));
        negativeText.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.yellow)), 0, negativeText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setNegativeButton(negativeText, (dialog, which) -> Reprint.cancelAuthentication());

        fingerprintDialog = builder.create();
        fingerprintDialog.setCanceledOnTouchOutside(true);
        // display dialog
        fingerprintDialog.show();
    }

    private void showFingerprintError(AuthenticationFailureReason reason, CharSequence message, View view) {
        if (isAdded()) {
            final HashMap<String, String> customData = new HashMap<>();
            customData.put("description", reason.name());
            TextView textView = view.findViewById(R.id.fingerprint_textview);
            textView.setText(message.toString());
            textView.setTextColor(ContextCompat.getColor(getContext(), R.color.error));
            textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_fingerprint_error, 0, 0, 0);
        }
    }
}
