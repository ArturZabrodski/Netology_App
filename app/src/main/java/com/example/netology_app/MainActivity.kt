package com.example.netology_app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognizerIntent.EXTRA_RESULTS
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    val TAG: String = "MainActivity"

    // переменная для текстового поля ввода
    lateinit var requestInput: TextInputEditText

    // переменная, которая отвечает за адаптер
    lateinit var podsAdapter: SimpleAdapter

    lateinit var progressBar: ProgressBar

    lateinit var waEngine: WAEngine

    // список с данными
    val pods = mutableListOf<HashMap<String, String>>(HashMap<String, String>())
//        .apply {
//        put("Title", "Title 1")
//        put("Content", "Content 1")
//    }, HashMap<String, String>().apply {
//        put("Title", "Title 2")
//        put("Content", "Content 2")
//    }, HashMap<String, String>().apply {
//        put("Title", "Title 3")
//        put("Content", "Content 3")
//    }, HashMap<String, String>().apply {
//        put("Title", "Title 4")
//        put("Content", "Content 4")
//    })

    lateinit var textToSpeech: TextToSpeech

    var isTtsReadly: Boolean = false

    val VOICE_RECOGNITION_REQUEST_CODE: Int = 777

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pods.clear()

        initViews()
        initWolframEngine()
        initTts()
    }

    fun initViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        // связываем toolbar с приложением
        setSupportActionBar(toolbar)

        requestInput = findViewById(R.id.text_input_edit)
        requestInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                pods.clear()
                // встряхиваем адаптер
                podsAdapter.notifyDataSetChanged()

                val question = requestInput.text.toString()
                askWolfram(question)
            }
            return@setOnEditorActionListener false
        }

        val podsList: ListView = findViewById(R.id.pods_list)
        podsAdapter = SimpleAdapter(
            applicationContext,
            pods,
            R.layout.item_pod,
            arrayOf("Title", "Content"),
            intArrayOf(R.id.title, R.id.content)
        )

        // указываем адаптер в качестве адаптера podsList
        podsList.adapter = podsAdapter

        podsList.setOnItemClickListener { parent, view, position, id ->
            if (isTtsReadly) {
                val title = pods[position]["Title"]
                val content = pods[position]["Content"]
                textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, title)
            }
        }

        // переменная вызова микрофона
        val voiceInputButton: FloatingActionButton = findViewById(R.id.voice_input_button)
        // добавляем действие
        voiceInputButton.setOnClickListener {
//            Log.d(TAG, "FAB")
            pods.clear()
            podsAdapter.notifyDataSetChanged()

            if (isTtsReadly) {
                textToSpeech.stop()
            }

            // чтобы sts не перебивал голосовой поиск
            showVoiceInputDialog()

        }
        progressBar = findViewById(R.id.progress_bar)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // здесь определим действия значков меню
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
//            R.id.action_language -> {
//                // выбор языка
//                return true
//            }
            R.id.action_clear -> {
                // Log.d(TAG, "actions_clear")
                // добавляем обработку - очистить список
                requestInput.text?.clear()
                pods.clear()
                podsAdapter.notifyDataSetChanged()
                return true
            }
            R.id.action_stop -> {
                // Log.d(TAG, "actions_stop")
                // если TTS готов, то обращаемся к методу
                if (isTtsReadly) {
                    textToSpeech.stop()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // GVWYL5-5KQTT39LQP
    fun initWolframEngine() {
        // зададим несколько параметров этого движка
        waEngine = WAEngine().apply {
            appID = "GVWYL5-5KQTT39LQP"
            addFormat("plaintext")
        }
    }

    fun showSnackbar(message: String) {
        // показывается бесконечно
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE)
            .apply {
                // кнопка OK, при нажатии будет исчезать
                setAction(android.R.string.ok) {
                    dismiss()
                }
                show()
            }
    }

    fun askWolfram(request: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply { input = request }
            runCatching {
                waEngine.performQuery(query)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    // обработать запрос-ответ
                    if (result.isError) {
                        showSnackbar(result.errorMessage)
                        return@withContext
                    }

                    if (!result.isSuccess) {
                        requestInput.error = getString(R.string.error_do_not_understand)
                        return@withContext
                    }

                    for (pod in result.pods) {
                        if (pod.isError) continue
                        val content = StringBuilder()
                        for (subpod in pod.subpods) {
                            for (element in subpod.contents) {
                                if (element is WAPlainText) {
                                    content.append(element.text)
                                }
                            }
                        }
                        // наполняем список какими-то элементами
                        pods.add(0, HashMap<String, String>().apply {
                            put("Title", pod.title)
                            put("Content", content.toString())
                        })
                    }
                    // делаем обязательно после цикла
                    podsAdapter.notifyDataSetChanged()
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    // обработать ошибку
                    progressBar.visibility = View.GONE
                    showSnackbar(t.message ?: getString(R.string.error_something_went_wrong))
                }
            }
        }
    }

    fun initTts() {
        textToSpeech = TextToSpeech(this) { code ->
            if (code != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS error code: $code")
                showSnackbar(getString(R.string.error_tts_is_not_ready))
            } else {
                isTtsReadly = true
            }
        }
        textToSpeech.language = Locale.US
    }

    // добавляем голосовой ввод
    fun showVoiceInputDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // используем свободную модель языка
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // подсказка на окне распознавания голоса
            putExtra(
                RecognizerIntent.EXTRA_PROMPT, getString(R.string.request_hint)
            )
            // используем английский язык, т.к. Wolfram понимает только его
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE, Locale.US
            )
        }
        // отправляем intent в систему
        runCatching {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
        }.onFailure { t ->
            showSnackbar(t.message ?: getString(R.string.error_voice_recognition_unavailable))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let { question ->
                requestInput.setText(question)
                askWolfram(question)
            }
        }
    }
}