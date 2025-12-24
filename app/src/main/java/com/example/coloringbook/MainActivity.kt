package com.example.coloringbook

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import com.example.coloringbook.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var svgColoringView: SVGColoringView
    private var adapter: ColorAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initView()
        loadSVG()

        binding.btn.setOnClickListener {
            val colorList = listOf(
                "#000000", // Đen
                "#FFFFFF", // Trắng
                "#FF0000", // Đỏ
                "#00FF00", // Xanh lá
                "#0000FF", // Xanh dương
                "#FFFF00", // Vàng
                "#FFA500", // Cam
                "#800080", // Tím
                "#FFC0CB", // Hồng
                "#A52A2A", // Nâu
                "#808080", // Xám
                "#00FFFF", // Cyan
                "#FF00FF"  // Magenta
            )
            svgColoringView.setFillColor(colorList.random())
        }
    }

    private fun initView() {
        svgColoringView = SVGColoringView(this, { list ->
            adapter?.updateColorPalette(list)
        })

        binding.svgColoringView.removeAllViews()
        binding.svgColoringView.addView(svgColoringView)
        adapter = ColorAdapter { color ->
//            svgColoringView.setSelectedColor(color)
        }
        binding.rvColor.adapter = adapter
        binding.rvColor.layoutManager = GridLayoutManager(
            this,
            1,
            GridLayoutManager.HORIZONTAL,
            false
        )
    }

    private fun loadSVG() {
        try {
            val svg = SVG.getFromResource(resources, R.raw.b)
            svgColoringView.setSVG(svg)
//            svgColoringView.setBackgroundImage(R.drawable.test3)
        } catch (e: SVGParseException) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi khi tải SVG: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}