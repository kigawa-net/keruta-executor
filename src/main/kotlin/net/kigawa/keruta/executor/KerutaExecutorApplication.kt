package net.kigawa.keruta.executor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Main application class for the Keruta Executor.
 * This application fetches tasks from keruta-api and executes them using coder.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class KerutaExecutorApplication

fun main(args: Array<String>) {
    runApplication<KerutaExecutorApplication>(*args)
}