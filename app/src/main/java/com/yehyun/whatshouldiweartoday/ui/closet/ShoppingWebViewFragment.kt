package com.yehyun.whatshouldiweartoday.ui.closet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentShoppingWebviewBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShoppingWebViewFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentShoppingWebviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShoppingWebViewViewModel by viewModels()

    private lateinit var platform: ShoppingPlatform
    private var hasRedirectedToPurchaseHistory = false
    private var wasOnLoginPage = false
    private var lastFabClickTime = 0L

    private var toast: Toast? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingBatchPaths?.let { startBatchAddWorker(it) }
            pendingBatchPaths = null
        } else {
            pendingBatchPaths = null
            if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                showGoToSettingsDialog()
            } else {
                showToast("알림 권한이 거부되어 기능을 실행할 수 없습니다.")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShoppingWebviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val platformName = arguments?.getString("platform_name") ?: return
        platform = ShoppingPlatform.valueOf(platformName)

        setupWebView()
        setupFabs()
        setupObservers()
        setupBackPressHandler()
        loadInitialUrl()
    }

    // JS to inject on every page to hide WebView fingerprints
    private val antiDetectionJs = """
        (function() {
            // Hide webdriver flag (Selenium/automation detection)
            Object.defineProperty(navigator, 'webdriver', { get: () => false });

            // Override plugins to look like real Chrome
            Object.defineProperty(navigator, 'plugins', {
                get: () => [1, 2, 3, 4, 5]
            });

            // Override languages
            Object.defineProperty(navigator, 'languages', {
                get: () => ['ko-KR', 'ko', 'en-US', 'en']
            });

            // Hide automation-related properties
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;

            // Override Chrome property
            window.chrome = {
                runtime: {},
                loadTimes: function() {},
                csi: function() {},
                app: { isInstalled: false }
            };

            // Override permissions query
            const originalQuery = window.navigator.permissions.query;
            window.navigator.permissions.query = (parameters) => (
                parameters.name === 'notifications' ?
                    Promise.resolve({ state: Notification.permission }) :
                    originalQuery(parameters)
            );
        })();
    """.trimIndent()

    private fun setupWebView() {
        val webView = binding.webView

        // Enable cookies for login persistence
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Build a realistic Chrome user agent (no "wv" WebView indicator)
        val chromeVersion = "131.0.6778.135"
        val realChromeUA = "Mozilla/5.0 (Linux; Android 14; SM-S928N Build/UP1A.231005.007) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = realChromeUA
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Handle intent:// URLs (some Korean apps use this)
                if (url.startsWith("intent://")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        if (intent.resolveActivity(requireContext().packageManager) != null) {
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                    return true
                }

                // Handle market:// URLs
                if (url.startsWith("market://")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        // Ignore
                    }
                    return true
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (_binding == null) return
                binding.progressBar.visibility = View.VISIBLE

                // Inject anti-detection JS as early as possible
                view?.evaluateJavascript(antiDetectionJs, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (_binding == null) return
                binding.progressBar.visibility = View.GONE

                // Re-inject after page fully loads (some sites check late)
                view?.evaluateJavascript(antiDetectionJs, null)

                // Flush cookies for persistence
                CookieManager.getInstance().flush()

                // Handle login detection and redirect to purchase history
                if (platform != ShoppingPlatform.GOOGLE) {
                    handleLoginRedirect(url)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (_binding == null) return
                binding.progressBar.progress = newProgress
                if (newProgress == 100) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun handleLoginRedirect(url: String?) {
        if (url == null) return

        // Case 1: We tried purchase history but got redirected to login (stale cookies)
        if (hasRedirectedToPurchaseHistory && platform.isLoginPage(url)) {
            hasRedirectedToPurchaseHistory = false
            wasOnLoginPage = true
            return
        }

        val isOnLogin = platform.isLoginPage(url)

        if (isOnLogin) {
            wasOnLoginPage = true
        }

        // Case 2: For platforms without redirect support (e.g. Musinsa),
        // detect navigation away from login page and redirect to purchase history
        if (!platform.supportsRedirect && wasOnLoginPage && !isOnLogin && !hasRedirectedToPurchaseHistory) {
            hasRedirectedToPurchaseHistory = true
            binding.webView.loadUrl(platform.purchaseHistoryUrl)
        }

        // For platforms WITH redirect support:
        // The login URL already contains redirect param,
        // so after login the site itself navigates to purchase history.
        // No manual redirect needed.
    }

    private fun loadInitialUrl() {
        // Check if cookies exist for the purchase history domain
        val purchaseDomain = Uri.parse(platform.purchaseHistoryUrl).host ?: ""
        val loginDomain = Uri.parse(platform.loginUrl).host ?: ""
        val purchaseCookies = CookieManager.getInstance().getCookie(purchaseDomain)
        val loginCookies = CookieManager.getInstance().getCookie(loginDomain)
        val hasCookies = !purchaseCookies.isNullOrEmpty() || !loginCookies.isNullOrEmpty()

        if (hasCookies && platform != ShoppingPlatform.GOOGLE) {
            // Cookies exist, try purchase history directly
            // If cookies are stale, handleLoginRedirect will detect the login redirect
            hasRedirectedToPurchaseHistory = true
            binding.webView.loadUrl(platform.purchaseHistoryUrl)
        } else {
            // No cookies - go to login URL (includes redirect param for supported platforms)
            binding.webView.loadUrl(platform.loginUrl)
        }
    }

    private fun setupFabs() {
        // Plus button - capture screenshot and detect clothing
        binding.fabCapture.setOnClickListener {
            if (System.currentTimeMillis() - lastFabClickTime < 700) return@setOnClickListener
            lastFabClickTime = System.currentTimeMillis()

            captureScreenAndDetect()
        }

        // Multi-add button - extract all images from webpage
        binding.fabBatchExtract.setOnClickListener {
            if (System.currentTimeMillis() - lastFabClickTime < 700) return@setOnClickListener
            lastFabClickTime = System.currentTimeMillis()

            showExtractAllDialog()
        }
    }

    private fun captureScreenAndDetect() {
        val webView = binding.webView
        try {
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)

            viewModel.detectClothingFromScreenshot(bitmap)
        } catch (e: Exception) {
            showToast("화면 캡처에 실패했습니다.")
        }
    }

    private fun showExtractAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("이미지 추출")
            .setMessage("현재 웹페이지 속 모든 옷 사진을 추가하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                extractImagesFromPage()
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun extractImagesFromPage() {
        binding.webView.evaluateJavascript("""
            (function() {
                var images = document.querySelectorAll('img');
                var srcs = [];
                images.forEach(function(img) {
                    var src = img.src || img.getAttribute('data-src') || img.getAttribute('data-lazy-src');
                    if (src && src.startsWith('http') && img.naturalWidth > 50 && img.naturalHeight > 50) {
                        srcs.push(src);
                    }
                });
                return JSON.stringify(srcs);
            })()
        """.trimIndent()) { result ->
            try {
                val cleaned = result.trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
                val urls = kotlinx.serialization.json.Json.decodeFromString<List<String>>(cleaned)

                if (urls.isEmpty()) {
                    showToast("웹페이지에서 이미지를 찾을 수 없습니다.")
                    return@evaluateJavascript
                }

                viewModel.extractAndFilterClothingImages(urls)
            } catch (e: Exception) {
                showToast("이미지 URL 추출에 실패했습니다.")
            }
        }
    }

    private fun setupObservers() {
        viewModel.isProcessing.observe(viewLifecycleOwner) { isProcessing ->
            if (_binding == null) return@observe
            binding.loadingOverlay.visibility = if (isProcessing) View.VISIBLE else View.GONE
        }

        viewModel.processingMessage.observe(viewLifecycleOwner) { message ->
            if (_binding == null) return@observe
            binding.tvLoadingMessage.text = message
        }

        viewModel.captureResult.observe(viewLifecycleOwner) { result ->
            handleCaptureResult(result)
        }

        viewModel.extractResult.observe(viewLifecycleOwner) { result ->
            handleCaptureResult(result)
        }
    }

    private fun handleCaptureResult(result: ShoppingWebViewViewModel.CaptureResult) {
        if (!result.success) {
            showToast(result.message)
            return
        }

        // Start batch add worker with the detected clothing images
        checkPermissionAndStartBatchAdd(result.imagePaths.toTypedArray())
    }

    private fun checkPermissionAndStartBatchAdd(imagePaths: Array<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startBatchAddWorker(imagePaths)
            } else {
                // Store paths and request permission
                pendingBatchPaths = imagePaths
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startBatchAddWorker(imagePaths)
        }
    }

    private var pendingBatchPaths: Array<String>? = null

    private fun startBatchAddWorker(imagePaths: Array<String>) {
        val workManager = androidx.work.WorkManager.getInstance(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val hasRunningWork = withContext(Dispatchers.IO) {
                try {
                    workManager.getWorkInfosForUniqueWork("batch_add").get()
                        .any { !it.state.isFinished }
                } catch (e: Exception) { false }
            }

            val batchId = "batch_shopping_${System.currentTimeMillis()}"
            val workRequest = OneTimeWorkRequestBuilder<BatchAddWorker>()
                .setInputData(
                    workDataOf(
                        BatchAddWorker.KEY_BATCH_ID to batchId,
                        BatchAddWorker.KEY_IMAGE_PATHS to imagePaths,
                        BatchAddWorker.KEY_API to getString(R.string.gemini_api_key)
                    )
                )
                .build()

            workManager.enqueueUniqueWork("batch_add", ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)

            if (hasRunningWork) {
                showToast("${imagePaths.size}개의 옷이 대기열에 추가되었습니다.")
            } else {
                showToast("${imagePaths.size}개의 옷을 추가하고 있습니다...")
            }
        }
    }

    private fun setupBackPressHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                }
                // Don't pop fragment - user uses bottom nav "옷" tab to exit
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("알림 권한이 필요합니다")
            .setMessage("진행률을 표시하려면 알림 권한이 반드시 필요합니다.")
            .setPositiveButton("예") { _, _ ->
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                }
                startActivity(intent)
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun showToast(message: String) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast?.show()
    }

    override fun onTabReselected() {
        findNavController().popBackStack(R.id.navigation_closet, false)
    }

    override fun onDestroyView() {
        binding.webView.destroy()
        toast?.cancel()
        super.onDestroyView()
        _binding = null
    }
}
