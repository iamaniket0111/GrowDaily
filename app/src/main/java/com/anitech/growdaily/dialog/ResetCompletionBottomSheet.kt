package com.anitech.growdaily.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.anitech.growdaily.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ResetCompletionBottomSheet(
    private val onResetConfirmed: () -> Unit
) : BottomSheetDialogFragment() {

    override fun getTheme(): Int {
        return R.style.TaskBottomSheetDialogTheme
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.bottom_sheet_reset_completion,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnReset).setOnClickListener {
            onResetConfirmed()
            dismiss()
        }

        view.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }
    }
}
