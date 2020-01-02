@file:Suppress("DEPRECATION")

package com.topjohnwu.magisk

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobWorkItem
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.RequiresApi
import com.topjohnwu.magisk.extensions.forceGetDeclaredField
import com.topjohnwu.magisk.model.download.DownloadService
import com.topjohnwu.magisk.model.receiver.GeneralReceiver
import com.topjohnwu.magisk.model.update.UpdateCheckService
import com.topjohnwu.magisk.ui.MainActivity
import com.topjohnwu.magisk.ui.SplashActivity
import com.topjohnwu.magisk.ui.flash.FlashActivity
import com.topjohnwu.magisk.ui.surequest.SuRequestActivity
import com.topjohnwu.magisk.utils.refreshLocale
import com.topjohnwu.magisk.utils.updateConfig
import com.topjohnwu.magisk.redesign.MainActivity as RedesignActivity

fun AssetManager.addAssetPath(path: String) {
    DynAPK.addAssetPath(this, path)
}

fun Context.wrap(global: Boolean = true): Context =
    if (global) GlobalResContext(this) else ResContext(this)

fun Context.wrapJob(): Context = object : GlobalResContext(this) {

    override fun getApplicationContext(): Context {
        return this
    }

    @SuppressLint("NewApi")
    override fun getSystemService(name: String): Any? {
        return if (!isRunningAsStub) super.getSystemService(name) else
            when (name) {
                Context.JOB_SCHEDULER_SERVICE ->
                    JobSchedulerWrapper(super.getSystemService(name) as JobScheduler)
                else -> super.getSystemService(name)
            }
    }
}

fun Class<*>.cmp(pkg: String): ComponentName {
    val name = ClassMap[this].name
    return ComponentName(pkg, Info.stub?.classToComponent?.get(name) ?: name)
}

inline fun <reified T> Context.intent() = Intent().setComponent(T::class.java.cmp(packageName))

private open class GlobalResContext(base: Context) : ContextWrapper(base) {
    open val mRes: Resources get() = ResourceMgr.resource

    override fun getResources(): Resources {
        return mRes
    }

    override fun getClassLoader(): ClassLoader {
        return javaClass.classLoader!!
    }

    override fun createConfigurationContext(config: Configuration): Context {
        return ResContext(super.createConfigurationContext(config))
    }
}

private class ResContext(base: Context) : GlobalResContext(base) {
    override val mRes by lazy { base.resources.patch() }

    private fun Resources.patch(): Resources {
        updateConfig()
        if (isRunningAsStub)
            assets.addAssetPath(ResourceMgr.resApk)
        return this
    }
}

object ResourceMgr {

    lateinit var resource: Resources
    lateinit var resApk: String

    fun init(context: Context) {
        resource = context.resources
        refreshLocale()
        if (isRunningAsStub) {
            resApk = DynAPK.current(context).path
            resource.assets.addAssetPath(resApk)
        }
    }
}

@RequiresApi(28)
private class JobSchedulerWrapper(private val base: JobScheduler) : JobScheduler() {

    override fun schedule(job: JobInfo): Int {
        return base.schedule(job.patch())
    }

    override fun enqueue(job: JobInfo, work: JobWorkItem): Int {
        return base.enqueue(job.patch(), work)
    }

    override fun cancel(jobId: Int) {
        base.cancel(jobId)
    }

    override fun cancelAll() {
        base.cancelAll()
    }

    override fun getAllPendingJobs(): List<JobInfo> {
        return base.allPendingJobs
    }

    override fun getPendingJob(jobId: Int): JobInfo? {
        return base.getPendingJob(jobId)
    }

    private fun JobInfo.patch(): JobInfo {
        // We need to swap out the service of JobInfo
        val name = service.className
        val component = ComponentName(
            service.packageName,
            Info.stub!!.classToComponent[name] ?: name
        )

        javaClass.forceGetDeclaredField("service")?.set(this, component)
        return this
    }
}

object ClassMap {

    private val map = mapOf(
        App::class.java to a.e::class.java,
        MainActivity::class.java to a.b::class.java,
        SplashActivity::class.java to a.c::class.java,
        FlashActivity::class.java to a.f::class.java,
        UpdateCheckService::class.java to a.g::class.java,
        GeneralReceiver::class.java to a.h::class.java,
        DownloadService::class.java to a.j::class.java,
        SuRequestActivity::class.java to a.m::class.java,
        ProcessPhoenix::class.java to a.r::class.java,
        RedesignActivity::class.java to a.i::class.java
    )

    operator fun get(c: Class<*>) = map.getOrElse(c) { c }
}
