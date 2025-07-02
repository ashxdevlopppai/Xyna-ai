package com.jarvis.ai.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarvis.ai.R
import com.jarvis.ai.adapter.ChatAdapter
import com.jarvis.ai.databinding.ActivityMainBinding
import com.jarvis.ai.model.Message
import com.jarvis.ai.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var chatAdapter: ChatAdapter
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewModel()
        setupRecyclerView()
        setupClickListeners()
        checkPermissions()
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages)
            binding.recyclerView.scrollToPosition(messages.size - 1)
        }
        
        viewModel.isListening.observe(this) { isListening ->
            binding.micButton.isSelected = isListening
        }
        
        viewModel.isProcessing.observe(this) { isProcessing ->
            binding.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
        }
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }
    
    private fun setupClickListeners() {
        binding.micButton.setOnClickListener {
            if (checkPermissions()) {
                viewModel.toggleVoiceInput()
            }
        }
        
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                viewModel.sendMessage(Message(content = message, isUser = true))
                binding.messageInput.text?.clear()
            }
        }
        
        binding.menuButton.setOnClickListener {
            binding.drawerLayout.open()
        }
    }
    
    private fun checkPermissions(): Boolean {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        return if (permissionsToRequest.isEmpty()) {
            true
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_CODE)
            false
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted
                viewModel.onPermissionsGranted()
            } else {
                // Show error message
                viewModel.showPermissionError()
            }
        }
    }
    
    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }
} 