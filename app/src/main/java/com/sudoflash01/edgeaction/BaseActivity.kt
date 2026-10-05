package com.sudoflash01.edgeaction

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.DynamicColors

// every screen extends this: picks dynamic colour or the default colour set, and owns the help sheet
abstract class BaseActivity : AppCompatActivity() {

    private var dynamicApplied = false

    private fun dynamicNow() = Cfg.dynamicWanted(this) && DynamicColors.isDynamicColorAvailable()

    override fun onCreate(savedInstanceState: Bundle?) {
        // must run before super.onCreate / setContentView so the colours reach every view
        dynamicApplied = dynamicNow()
        if (dynamicApplied) DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // the toggle was flipped on another screen: rebuild this one so it shows the right colours
        if (dynamicNow() != dynamicApplied) recreate()
    }

    fun showHelp() {
        BottomSheetDialog(this).apply {
            setContentView(layoutInflater.inflate(R.layout.sheet_help, null))
            show()
        }
    }
}
