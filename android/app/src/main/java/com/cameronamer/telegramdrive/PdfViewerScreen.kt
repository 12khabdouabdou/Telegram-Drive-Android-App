package com.cameronamer.telegramdrive

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient

@Composable
fun PdfViewerScreen(pdfUrl: String) {
    val context = LocalContext.current

    // For a fully native experience without a 3rd party PDF library, 
    // Android often uses an Intent to view PDFs, or Google Docs viewer in a WebView
    // Here we provide a simple WebView pointing to the Google Docs PDF renderer as a fallback,
    // though a real app might use PdfRenderer API.
    
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                loadUrl("https://docs.google.com/gview?embedded=true&url=$pdfUrl")
            }
        }
    )
}