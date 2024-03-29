package de.julianhofmann.h_bank.ui.main.home;

import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import de.julianhofmann.h_bank.R;
import de.julianhofmann.h_bank.ui.main.MainActivity;
import de.julianhofmann.h_bank.util.SettingsService;

public class HomeFragment extends Fragment {

    private final Handler refreshBalanceHandler = new Handler();
    private boolean paused = false;
    private Runnable refreshBalanceRunnable;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        refreshBalanceRunnable = new Runnable() {
            @Override
            public void run() {
                ((MainActivity) requireActivity()).loadUserInfo(null);
                refreshBalanceHandler.postDelayed(this, SettingsService.getAutoRefreshInterval());
            }
        };

        ((MainActivity) requireActivity()).loadUserInfo(null);
        if (SettingsService.getAutoRefresh()) {
            refreshBalanceHandler.postDelayed(refreshBalanceRunnable, SettingsService.getAutoRefreshInterval());
        }

        FloatingActionButton refresh = requireView().findViewById(R.id.user_refresh_button);
        refresh.setVisibility(SettingsService.getAutoRefresh() ? View.GONE : View.VISIBLE);

        EditText cash = requireView().findViewById(R.id.cash_input);
        cash.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                ((MainActivity) requireActivity()).updateCash();
                cash.clearFocus();
            }

            return false;
        });

        new Handler().postDelayed(() -> {
            if (((TextView) requireView().findViewById(R.id.user_name_lbl)).getText().length() == 0) {
                ((MainActivity) requireActivity()).loadUserInfo(null);
            }
        }, 200);
    }

    private int toDp(int px) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px, getResources().getDisplayMetrics());
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
        refreshBalanceHandler.removeCallbacks(refreshBalanceRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (paused) {
            ((MainActivity) requireActivity()).loadUserInfo(null);
            if (SettingsService.getAutoRefresh()) {
                refreshBalanceHandler.postDelayed(refreshBalanceRunnable, SettingsService.getAutoRefreshInterval());
            }

            paused = false;
        }

        FloatingActionButton refresh = requireView().findViewById(R.id.user_refresh_button);
        refresh.setVisibility(SettingsService.getAutoRefresh() ? View.GONE : View.VISIBLE);

        int visibility = SettingsService.getCashNoteFunction() ? View.VISIBLE : View.INVISIBLE;
        TextView balanceCurrency = requireView().findViewById(R.id.balance_currency);
        balanceCurrency.setVisibility(visibility);
        TextView cashLbl = requireView().findViewById(R.id.cash_lbl);
        cashLbl.setVisibility(visibility);
        TextView cashInput = requireView().findViewById(R.id.cash_input);
        cashInput.setVisibility(visibility);
        TextView cashCurrency = requireView().findViewById(R.id.cash_currency_lbl);
        cashCurrency.setVisibility(visibility);
        TextView lastCashEdit = requireView().findViewById(R.id.last_cash_edit);
        lastCashEdit.setVisibility(visibility);

        FloatingActionButton paymentPlanBtn = requireView().findViewById(R.id.home_payment_plan_btn);

        DisplayMetrics metrics = getResources().getDisplayMetrics();


        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        if (SettingsService.getCashNoteFunction() && metrics.heightPixels / metrics.density < 715) {
            params.setMargins(toDp(24), toDp(0), toDp(0), toDp(80));
            paymentPlanBtn.setLayoutParams(params);

            ConstraintLayout layout = requireView().findViewById(R.id.home_constraint_layout);
            ConstraintSet set = new ConstraintSet();
            set.clone(layout);
            set.connect(R.id.home_payment_plan_btn, ConstraintSet.START, R.id.home_constraint_layout, ConstraintSet.START);
            set.connect(R.id.home_payment_plan_btn, ConstraintSet.BOTTOM, R.id.home_constraint_layout, ConstraintSet.BOTTOM);
            set.clear(R.id.home_payment_plan_btn, ConstraintSet.END);
            set.applyTo(layout);
        } else {
            params.setMargins(toDp(0), toDp(0), toDp(24), toDp(16));
            paymentPlanBtn.setLayoutParams(params);

            ConstraintLayout layout = requireView().findViewById(R.id.home_constraint_layout);
            ConstraintSet set = new ConstraintSet();
            set.clone(layout);
            set.connect(R.id.home_payment_plan_btn, ConstraintSet.END, R.id.home_constraint_layout, ConstraintSet.END);
            set.connect(R.id.home_payment_plan_btn, ConstraintSet.BOTTOM, R.id.home_calculator_btn, ConstraintSet.TOP);
            set.clear(R.id.home_payment_plan_btn, ConstraintSet.START);
            set.applyTo(layout);
        }
    }
}