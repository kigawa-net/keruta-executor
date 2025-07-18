package net.kigawa.keruta.executor.service

import net.kigawa.keruta.executor.config.KerutaExecutorProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Component that processes tasks in the background.
 * It periodically fetches tasks from keruta-api and executes them using coder.
 */
@Component
class TaskProcessor(
    private val taskApiService: TaskApiService,
    private val coderExecutionService: CoderExecutionService,
    private val properties: KerutaExecutorProperties
) {
    private val logger = LoggerFactory.getLogger(TaskProcessor::class.java)
    private val isProcessing = AtomicBoolean(false)

    /**
     * Scheduled method that processes the next task in the queue.
     * It ensures that only one task is processed at a time.
     */
    @Scheduled(fixedDelayString = "#{@processingDelayMillis}")
    fun processNextTask() {
        // If already processing a task, skip this run
        if (!isProcessing.compareAndSet(false, true)) {
            logger.debug("Already processing a task, skipping this run")
            return
        }

        try {
            logger.info("Checking for tasks in the queue")

            // Get the next task from the queue
            val task = taskApiService.getNextPendingTask()
            if (task == null) {
                logger.debug("No tasks in the queue")
                return
            }

            logger.info("Processing task ${task.id}")

            // Execute the task
            val success = coderExecutionService.executeTask(task)
            if (success) {
                logger.info("Task ${task.id} executed successfully")
            } else {
                logger.warn("Task ${task.id} execution failed")
            }
        } catch (e: Exception) {
            logger.error("Error processing next task", e)
        } finally {
            isProcessing.set(false)
        }
    }
}
