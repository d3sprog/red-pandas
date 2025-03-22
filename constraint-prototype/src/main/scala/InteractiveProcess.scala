package red_pandas

import java.io.*
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.*
import scala.util.{Try, Success, Failure}
import scala.jdk.CollectionConverters.*

/**
 * A class for managing an interactive process (like a REPL) with redirected
 * stdin, stdout, and stderr streams.
 *
 * @param command The command and arguments to start the process
 * @param workingDir Optional working directory for the process
 * @param environment Optional environment variables for the process
 */
class InteractiveProcess(
    command: Seq[String],
    workingDir: Option[File] = None,
    environment: Map[String, String] = Map.empty
):
  private var process: Option[Process] = None
  private var stdin: Option[BufferedWriter] = None
  private var stdoutThread: Option[Thread] = None
  private var stderrThread: Option[Thread] = None
  
  private val stdoutQueue = LinkedBlockingQueue[String]()
  private val stderrQueue = LinkedBlockingQueue[String]()
  
  private var isRunning = false

  /**
   * Starts the process and sets up the IO streams.
   *
   * @return Success if the process started successfully, Failure otherwise
   */
  def start(): Try[Unit] = Try {
    val pb = ProcessBuilder(command.asJava)
    workingDir.foreach(pb.directory)
    
    if environment.nonEmpty then
      pb.environment().putAll(environment.asJava)
    
    val p = pb.start()
    process = Some(p)
    
    stdin = Some(BufferedWriter(OutputStreamWriter(p.getOutputStream)))
    
    // Start thread to read stdout
    val stdoutWorker = new Thread {
      override def run(): Unit = {
        val reader = BufferedReader(InputStreamReader(p.getInputStream))
        try
          var line: String = null
          while { line = reader.readLine(); line != null && isRunning } do
            stdoutQueue.put(line)
        catch
          case e: IOException if isRunning =>
            System.err.println(s"Error reading stdout: ${e.getMessage}")
        finally
          reader.close()
      }
    }
    
    // Start thread to read stderr
    val stderrWorker = new Thread {
      override def run(): Unit = {
        val reader = BufferedReader(InputStreamReader(p.getErrorStream))
        try 
          var line: String = null
          while { line = reader.readLine(); line != null && isRunning } do
            stderrQueue.put(line)
        catch
          case e: IOException if isRunning =>
            System.err.println(s"Error reading stderr: ${e.getMessage}")
        finally
          reader.close()
      }
    }
    
    isRunning = true
    stdoutWorker.setDaemon(true)
    stderrWorker.setDaemon(true)
    stdoutWorker.start()
    stderrWorker.start()
    
    stdoutThread = Some(stdoutWorker)
    stderrThread = Some(stderrWorker)
  }
  
  /**
   * Sends a command to the process.
   *
   * @param input The input to send
   * @param appendNewline Whether to append a newline to the input
   * @return Future[Unit] completing when the command is sent
   */
  def sendCommand(input: String, appendNewline: Boolean = true)(using ExecutionContext): Future[Unit] = {
    val promise = Promise[Unit]()
    Future {
      try
        stdin match
          case Some(writer) =>
            writer.write(input)
            if appendNewline then writer.newLine()
            writer.flush()
            promise.success(())
          case None =>
            promise.failure(IllegalStateException("Process not started or already terminated"))
      catch
        case e: Exception => 
          promise.failure(e)
    }
    promise.future
  }
  
  /**
   * Reads output from stdout.
   *
   * @param timeout How long to wait for output
   * @param maxLines Maximum number of lines to read (0 for all available)
   * @return The collected output as a string
   */
  def readOutput(timeout: Duration = 1.second, maxLines: Int = 0): String = {
    val result = new StringBuilder
    val endTime = System.nanoTime() + timeout.toNanos
    
    var lineCount = 0
    while (
      System.nanoTime() < endTime && 
      (maxLines <= 0 || lineCount < maxLines)
    ) {
      val line = stdoutQueue.poll(
        math.max(1, (endTime - System.nanoTime()) / 1_000_000), 
        TimeUnit.MILLISECONDS
      )
      
      if line != null then
        result.append(line).append(System.lineSeparator())
        lineCount += 1
    }
    
    result.toString()
  }
  
  /**
   * Reads error output from stderr.
   *
   * @param timeout How long to wait for output
   * @param maxLines Maximum number of lines to read (0 for all available)
   * @return The collected error output as a string
   */
  def readError(timeout: Duration = 1.second, maxLines: Int = 0): String = {
    val result = new StringBuilder
    val endTime = System.nanoTime() + timeout.toNanos
    
    var lineCount = 0
    while (
      System.nanoTime() < endTime && 
      (maxLines <= 0 || lineCount < maxLines)
    ) {
      val line = stderrQueue.poll(
        math.max(1, (endTime - System.nanoTime()) / 1_000_000), 
        TimeUnit.MILLISECONDS
      )
      
      if line != null then
        result.append(line).append(System.lineSeparator())
        lineCount += 1
    }
    
    result.toString()
  }
  
  /**
   * Convenience method to send a command and wait for a response.
   *
   * @param input The input to send
   * @param waitTime How long to wait for a response
   * @param appendNewline Whether to append a newline to the input
   * @return The response from the process
   */
  def sendAndReceive(
    input: String, 
    waitTime: Duration = 1.second, 
    appendNewline: Boolean = true
  )(using ExecutionContext): Future[String] = {
    // Clear any existing output
    drainOutput()
    
    sendCommand(input, appendNewline).map { _ =>
      Thread.sleep(waitTime.toMillis)
      readOutput(waitTime)
    }
  }
  
  /**
   * Clears any pending output from the queues.
   */
  def drainOutput(): Unit = {
    stdoutQueue.clear()
    stderrQueue.clear()
  }
  
  /**
   * Checks if the process is alive.
   *
   * @return true if the process is running, false otherwise
   */
  def isAlive: Boolean = process.exists(_.isAlive)
  
  /**
   * Stops the process and cleans up resources.
   */
  def stop(): Unit = {
    isRunning = false
    
    // Close stdin
    stdin.foreach { writer =>
      try
        writer.close()
      catch
        case _: IOException => // Ignore
    }
    
    // Terminate the process
    process.foreach { p =>
      if p.isAlive then
        p.destroy()
        try 
          if !p.waitFor(3, TimeUnit.SECONDS) then
            p.destroyForcibly()
        catch
          case _: InterruptedException => 
            p.destroyForcibly()
    }
    
    // Clear resources
    stdin = None
    process = None
  }
end InteractiveProcess
