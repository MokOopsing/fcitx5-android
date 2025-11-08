/*
 * Minimal external AIDL client to link with Yanxi (asr-keyboard)
 * for connectivity test via vendorId = "mock".
 */
package org.fcitx.fcitx5.android.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.R

object AsrkbSpeechClient {
    private const val TAG = "AsrkbLink"
    private var bound = false
    private var connection: ServiceConnection? = null
    private var remote: IBinder? = null
    private var sessionId: Int = -1
    private var currentState: Int = STATE_IDLE
    private var holding: Boolean = false
    private var ctxRef: Context? = null

    fun startHoldSession(service: FcitxInputMethodService) {
        if (bound && remote != null && sessionId > 0) return
        val ctx = service
        ctxRef = ctx
        holding = true
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val b = binder ?: throw IllegalStateException("no binder")
                    remote = b
                    // 准备回调 Binder：仅处理 onFinal，其余忽略
                    val cbBinder = object : Binder() {
                        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                            return try {
                                when (code) {
                                    CB_onState -> {
                                        data.enforceInterface(DESCRIPTOR_CB)
                                        val _sid = data.readInt(); val s = data.readInt(); data.readString()
                                        currentState = s
                                        reply?.writeNoException(); true
                                    }
                                    CB_onPartial -> {
                                        data.enforceInterface(DESCRIPTOR_CB)
                                        val _sid = data.readInt(); val text = data.readString() ?: ""
                                        service.lifecycleScope.launch {
                                            service.currentInputConnection?.setComposingText(text, 1)
                                        }
                                        reply?.writeNoException(); true
                                    }
                                    CB_onFinal -> {
                                        data.enforceInterface(DESCRIPTOR_CB)
                                        val _sid = data.readInt()
                                        val text = data.readString() ?: ""
                                        service.lifecycleScope.launch {
                                            service.finishComposing()
                                            service.commitText(text)
                                            // 会话完成：通知覆盖层隐藏
                                            runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
                                            unbind()
                                        }
                                        reply?.writeNoException(); true
                                    }
                                    CB_onError -> {
                                        data.enforceInterface(DESCRIPTOR_CB)
                                        val _sid = data.readInt()
                                        val codeVal = data.readInt()
                                        val msg = data.readString()
                                        toast(ctx, mapCallbackError(ctx, codeVal, msg))
                                        // 出错时也隐藏覆盖层
                                        runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
                                        unbind()
                                        reply?.writeNoException(); true
                                    }
                                    CB_onAmplitude -> {
                                        data.enforceInterface(DESCRIPTOR_CB)
                                        val _sid = data.readInt(); val amp = data.readFloat()
                                        service.lifecycleScope.launch { runCatching { VoiceOverlayUiBridge.onAmplitude?.invoke(amp) } }
                                        reply?.writeNoException(); true
                                    }
                                    IBinder.INTERFACE_TRANSACTION -> { reply?.writeString(DESCRIPTOR_CB); true }
                                    else -> super.onTransact(code, data, reply, flags)
                                }
                            } catch (t: Throwable) {
                                Log.w(TAG, "callback transact handle failed", t)
                                false
                            }
                        }
                    }

                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    var sid = -999
                    try {
                        data.writeInterfaceToken(DESCRIPTOR_SVC)
                        // 不发送配置（presence=0）：完全遵循言犀当前设置（含流式/非流式）
                        data.writeInt(0)
                        // write callback binder
                        data.writeStrongBinder(cbBinder)
                        b.transact(TRANSACTION_startSession, data, reply, 0)
                        reply.readException()
                        sid = reply.readInt()
                    } finally {
                        try { data.recycle() } catch (_: Throwable) {}
                        try { reply.recycle() } catch (_: Throwable) {}
                    }
                    if (sid <= 0) {
                        toast(ctx, mapStartError(ctx, sid))
                        unbind()
                    }
                    else { sessionId = sid; currentState = STATE_RECORDING }
                } catch (t: Throwable) {
                    Log.w(TAG, "bind/start failed", t)
                    toast(ctx, "无法连接言犀服务")
                    unbind()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) { unbind() }
        }
        connection = conn
        // 依次尝试 Pro 包与开源包
        val candidates = listOf(
            ComponentName("com.brycewg.asrkb.pro", "com.brycewg.asrkb.api.ExternalSpeechService"),
            ComponentName("com.brycewg.asrkb", "com.brycewg.asrkb.api.ExternalSpeechService")
        )
        for (c in candidates) {
            val intent = Intent().apply { component = c }
            try {
                bound = ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                if (bound) break
            } catch (t: Throwable) {
                Log.d(TAG, "bind attempt failed: ${c.packageName}", t)
            }
        }
        if (!bound) { toast(ctx, "未找到言犀服务（Pro/开源）"); unbind() }
    }

    fun stopHoldSession() {
        if (!holding) return
        holding = false
        when (currentState) {
            STATE_RECORDING -> stopSession()
            STATE_PROCESSING -> cancelSession()
            else -> cancelSession()
        }
    }

    // 与服务端保持一致的接口描述符与事务号
    private const val DESCRIPTOR_SVC = "com.brycewg.asrkb.aidl.IExternalSpeechService"
    private const val TRANSACTION_startSession = IBinder.FIRST_CALL_TRANSACTION + 0
    private const val TRANSACTION_stopSession = IBinder.FIRST_CALL_TRANSACTION + 1
    private const val TRANSACTION_cancelSession = IBinder.FIRST_CALL_TRANSACTION + 2

    private const val DESCRIPTOR_CB = "com.brycewg.asrkb.aidl.ISpeechCallback"
    private const val CB_onState = IBinder.FIRST_CALL_TRANSACTION + 0
    private const val CB_onPartial = IBinder.FIRST_CALL_TRANSACTION + 1
    private const val CB_onFinal = IBinder.FIRST_CALL_TRANSACTION + 2
    private const val CB_onError = IBinder.FIRST_CALL_TRANSACTION + 3
    private const val CB_onAmplitude = IBinder.FIRST_CALL_TRANSACTION + 4

    private const val STATE_IDLE = 0
    private const val STATE_RECORDING = 1
    private const val STATE_PROCESSING = 2
    private const val STATE_ERROR = 3

    private fun unbind() {
        val ctx = ctxRef
        try { if (bound && connection != null && ctx != null) ctx.unbindService(connection!!) } catch (_: Throwable) {}
        bound = false
        connection = null
        remote = null
        sessionId = -1
        currentState = STATE_IDLE
        holding = false
        ctxRef = null
    }

    private fun stopSession() {
        val b = remote ?: return
        if (sessionId <= 0) return
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR_SVC)
            data.writeInt(sessionId)
            b.transact(TRANSACTION_stopSession, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.w(TAG, "stopSession failed", t)
        } finally { try { data.recycle() } catch (_: Throwable) {}; try { reply.recycle() } catch (_: Throwable) {} }
    }

    private fun cancelSession() {
        val b = remote ?: return
        if (sessionId <= 0) return
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR_SVC)
            data.writeInt(sessionId)
            b.transact(TRANSACTION_cancelSession, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.w(TAG, "cancelSession failed", t)
        } finally { try { data.recycle() } catch (_: Throwable) {}; try { reply.recycle() } catch (_: Throwable) {} }
    }

    private fun toast(ctx: Context, msg: String) {
        try {
            ContextCompat.getMainExecutor(ctx).execute {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Throwable) { }
    }

    private fun mapStartError(ctx: Context, code: Int): String {
        return when (code) {
            -2 -> ctx.getString(R.string.asrkb_err_busy)
            -3 -> ctx.getString(R.string.asrkb_err_feature_disabled)
            -4 -> ctx.getString(R.string.asrkb_err_mic_permission)
            else -> ctx.getString(R.string.asrkb_err_start_failed_with_code, code)
        }
    }

    private fun mapCallbackError(ctx: Context, code: Int, msg: String?): String {
        return when (code) {
            401 -> ctx.getString(R.string.asrkb_err_mic_permission)
            403 -> ctx.getString(R.string.asrkb_err_feature_disabled)
            else -> ctx.getString(R.string.asrkb_err_service_error_with_code, code)
        }
    }
}
