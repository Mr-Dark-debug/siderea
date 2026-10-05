package io.github.mrdarkdebug.siderea.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mrdarkdebug.siderea.core.camera.capability.CameraCapabilityReader
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilityRepository
import io.github.mrdarkdebug.siderea.core.camera.capability.CapabilitySource
import io.github.mrdarkdebug.siderea.core.camera.engine.CameraEngine
import io.github.mrdarkdebug.siderea.core.data.settings.CameraStateRepository
import io.github.mrdarkdebug.siderea.core.data.settings.SettingsRepository
import io.github.mrdarkdebug.siderea.core.data.settings.settingsDataStore
import javax.inject.Singleton

/**
 * Wires the plain-Kotlin core modules into Hilt. The core modules deliberately know nothing about
 * Hilt, so they stay trivially testable and reusable by the capture service later.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun settingsRepository(
        @ApplicationContext context: Context,
    ): SettingsRepository = SettingsRepository(context.settingsDataStore())

    @Provides
    @Singleton
    fun capabilitySource(
        @ApplicationContext context: Context,
    ): CapabilitySource = CameraCapabilityReader(context)

    @Provides
    @Singleton
    fun capabilityRepository(source: CapabilitySource): CapabilityRepository = CapabilityRepository(source)

    @Provides
    @Singleton
    fun cameraStateRepository(
        @ApplicationContext context: Context,
    ): CameraStateRepository = CameraStateRepository(context.settingsDataStore())

    /** One engine for the whole process: it owns the camera thread and at most one open camera. */
    @Provides
    @Singleton
    fun cameraEngine(
        @ApplicationContext context: Context,
    ): CameraEngine = CameraEngine(context)
}
