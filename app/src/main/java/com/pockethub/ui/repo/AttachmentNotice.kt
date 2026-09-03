package com.pockethub.ui.repo

// Shared formatter for attachment pick rejection (oversize image).

import android.content.Context
import com.pockethub.R

fun formatTooLarge(context: Context, sizeBytes: Int): String =
    if (sizeBytes > 0) {
        // Pass the raw Double — the resource's %.1f does the formatting.
        // (Pre-formatting to String here crashes: f != java.lang.String.)
        context.getString(R.string.attachment_too_large, sizeBytes / (1024.0 * 1024.0))
    } else {
        context.getString(R.string.attachment_too_large_unknown)
    }
