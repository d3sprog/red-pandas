package red_pandas

package ipython

import io.circe._
import io.circe.parser.parse
import java.io.File
import scala.sys.process._
import scala.io.Source
import java.util.UUID
import org.zeromq.{ZMQ, ZContext}
import org.zeromq.ZMQ.{Socket => ZMQSocket}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import java.nio.charset.StandardCharsets
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Hex

/**
 * A client for interacting with an IPython kernel directly using ZeroMQ.
 */
class IPythonKernel(
  private val connectionInfo: Map[String, Json],
  private val process: Option[Process] = None,
  private val connectionFile: Option[File] = None
) {
  private val context = new ZContext()
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
  
  // Parse connection info
  private val ip = connectionInfo("ip").asString.get
  private val transport = connectionInfo("transport").asString.get
  private val signatureScheme = connectionInfo("signature_scheme").asString.get
  private val key = connectionInfo("key").asString.get
  
  // Socket ports
  private val shellPort = connectionInfo("shell_port").asNumber.get.toInt.get
  private val controlPort = connectionInfo("control_port").asNumber.get.toInt.get
  private val iopubPort = connectionInfo("iopub_port").asNumber.get.toInt.get
  private val stdinPort = connectionInfo("stdin_port").asNumber.get.toInt.get
  
  // Session ID for this client
  private val sessionId = UUID.randomUUID().toString
  
  // Create sockets
  private val shellSocket = createSocket(ZMQ.DEALER, shellPort)
  private val controlSocket = createSocket(ZMQ.DEALER, controlPort)
  private val iopubSocket = createSocket(ZMQ.SUB, iopubPort)
  private val stdinSocket = createSocket(ZMQ.DEALER, stdinPort)
  
  // Initialize SUB socket to receive all messages
  iopubSocket.subscribe(Array.emptyByteArray)
  
  private def createSocket(socketType: Int, port: Int): ZMQSocket = {
    val socket = context.createSocket(socketType)
    socket.connect(s"$transport://$ip:$port")
    socket
  }
  
  /**
   * Signs a message using the kernel's key and signature scheme.
   * @param messageParts The message parts to sign (header, parent header, metadata, content)
   * @return The signature as a hex string
   */
  private def signMessage(messageParts: Seq[Array[Byte]]): String = {
    if (key.isEmpty) {
      // If no key is provided, return an empty signature
      return ""
    }
    
    val algorithm = signatureScheme match {
      case "hmac-sha256" => "HmacSHA256"
      case _ => throw new IllegalArgumentException(s"Unsupported signature scheme: $signatureScheme")
    }
    
    val mac = Mac.getInstance(algorithm)
    val keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm)
    mac.init(keySpec)
    
    // Update the MAC with each part of the message
    messageParts.foreach(mac.update)
    
    // Generate the signature
    Hex.encodeHexString(mac.doFinal())
  }

  /**
   * Evaluates Python code and returns the result as a string.
   * @param code The Python code to execute
   * @return The string representation of the result
   */
  def eval(code: String): String = {
    // Create a unique message ID
    val msgId = UUID.randomUUID().toString
    
    // Create the header
    val header = Map(
      "msg_id" -> msgId,
      "username" -> "scala_client",
      "session" -> sessionId,
      "date" -> java.time.Instant.now().toString,
      "msg_type" -> "execute_request",
      "version" -> "5.3"
    )
    
    // Create the content
    val content = Map(
      "code" -> code,
      "silent" -> false,
      "store_history" -> true,
      "user_expressions" -> Map.empty[String, String],
      "allow_stdin" -> false,
      "stop_on_error" -> true
    )
    
    // Convert the parts to JSON strings
    val headerJson = mapper.writeValueAsString(header).getBytes(StandardCharsets.UTF_8)
    val parentHeaderJson = "{}".getBytes(StandardCharsets.UTF_8)
    val metadataJson = "{}".getBytes(StandardCharsets.UTF_8)
    val contentJson = mapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8)
    
    // Generate the signature
    val signature = signMessage(Seq(headerJson, parentHeaderJson, metadataJson, contentJson))
    
    // Assemble the message
    val message = Seq(
      "<IDS|MSG>".getBytes(StandardCharsets.UTF_8),
      signature.getBytes(StandardCharsets.UTF_8),
      headerJson,
      parentHeaderJson,
      metadataJson,
      contentJson
    )
    
    // Send the message to the shell socket
    message.foreach(part => shellSocket.send(part, ZMQ.SNDMORE))
    shellSocket.send(Array.empty[Byte], 0)
    
    // Wait for the execute_result or error message
    var result: Option[String] = None
    val startTime = System.currentTimeMillis()
    val timeoutMs = 1000 // 30 seconds timeout

    val poller = context.createPoller(1)
    poller.register(iopubSocket, ZMQ.Poller.POLLIN)
    
    while (result.isEmpty && (System.currentTimeMillis() - startTime < timeoutMs)) {
      // Check if there's a message from the IOPub socket
      if (poller.poll(10) > 0 && poller.pollin(0)) {
        // Read the message
        val messageParts = new scala.collection.mutable.ArrayBuffer[Array[Byte]]()
        var hasMore = true
        
        while (hasMore) {
          val part = iopubSocket.recv(0)
          messageParts += part
          hasMore = iopubSocket.hasReceiveMore()
        }
        
        // Parse the message header to get its type
        if (messageParts.size >= 6) {
          val headerJson = new String(messageParts(3), StandardCharsets.UTF_8)
          val contentJson = new String(messageParts(6), StandardCharsets.UTF_8)
          
          val headerMap = mapper.readValue(headerJson, classOf[Map[String, String]])
          val msgType = headerMap("msg_type")
          
          if (msgType == "execute_result" || msgType == "error" || msgType == "stream") {
            val contentMap = mapper.readValue(contentJson, classOf[Map[String, Any]])
            
            if (msgType == "execute_result") {
              // Extract the text representation of the result
              val data = contentMap("data").asInstanceOf[Map[String, Any]]
              result = Some(data.getOrElse("text/plain", "").toString)
            } else if (msgType == "error") {
              // Extract the error message
              val errorName = contentMap("ename").toString
              val errorValue = contentMap("evalue").toString
              result = Some(s"$errorName: $errorValue")
            } else if (msgType == "stream") {
              // Handle output from print statements
              val text = contentMap("text").toString
              val name = contentMap("name").toString // stdout or stderr
              
              // Sometimes results come through stream messages (especially with print())
              if (name == "stdout" && text.trim.nonEmpty) {
                result = Some(text.trim)
              }
            }
          }
        }
      } else {
        // Wait a bit before checking again
        Thread.sleep(10)
      }
    }
    
    // Clean up poller
    poller.close()
    
    // Return the result or timeout message
    result.getOrElse("Execution timed out")
  }

  /**
   * Closes the kernel connection and shuts down the kernel process if it was started by this instance.
   * This method should be called when you're done with the kernel to release resources.
   */
  def close(): Unit = {
    println("Closing IPython kernel connection...")
    
    // Send shutdown request to the control channel
    try {
      val msgId = UUID.randomUUID().toString
      val header = Map(
        "msg_id" -> msgId,
        "username" -> "scala_client",
        "session" -> sessionId,
        "date" -> java.time.Instant.now().toString,
        "msg_type" -> "shutdown_request",
        "version" -> "5.3"
      )
      
      val content = Map(
        "restart" -> false
      )
      
      // Convert to JSON
      val headerJson = mapper.writeValueAsString(header).getBytes(StandardCharsets.UTF_8)
      val parentHeaderJson = "{}".getBytes(StandardCharsets.UTF_8)
      val metadataJson = "{}".getBytes(StandardCharsets.UTF_8)
      val contentJson = mapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8)
      
      // Sign the message
      val signature = signMessage(Seq(headerJson, parentHeaderJson, metadataJson, contentJson))
      
      // Assemble the message
      val message = Seq(
        "<IDS|MSG>".getBytes(StandardCharsets.UTF_8),
        signature.getBytes(StandardCharsets.UTF_8),
        headerJson,
        parentHeaderJson,
        metadataJson,
        contentJson
      )
      
      message.foreach(controlSocket.send(_, ZMQ.SNDMORE))
      controlSocket.send(Array.empty[Byte], 0)
      
      // Wait briefly for the kernel to respond
      Thread.sleep(1000)
    } catch {
      case e: Exception =>
        println(s"Error during kernel shutdown request: ${e.getMessage}")
    }
    
    // Close ZMQ sockets
    try {
      println("Closing ZMQ sockets...")
      shellSocket.close()
      controlSocket.close()
      iopubSocket.close()
      stdinSocket.close()
      context.close()
    } catch {
      case e: Exception =>
        println(s"Error closing ZMQ sockets: ${e.getMessage}")
    }
    
    // Terminate the process if we own it
    process.foreach { p =>
      println("Terminating kernel process...")
      p.destroy()
    }
    
    // Clean up connection file
    connectionFile.foreach { file =>
      if (file.exists()) {
        println(s"Deleting connection file: ${file.getAbsolutePath}")
        file.delete()
      }
    }
  }
}

object IPythonKernel {
  private val CONNECTION_FILE_PATH = new File(System.getProperty("user.dir"), "ipython_kernel_connection.json").getAbsolutePath
  
  /**
   * Starts an IPython kernel and returns a configured IPythonKernel instance.
   * 
   * @param timeout Maximum time to wait for kernel to start (in seconds)
   * @return A configured IPythonKernel instance
   */
  def start(timeout: Int = 30): IPythonKernel = {
    // Make sure we have a clean connection file
    val connectionFile = new File(CONNECTION_FILE_PATH)
    if (connectionFile.exists()) {
      connectionFile.delete()
    }
    
    // Start IPython kernel with connection file
    println("Starting IPython kernel...")
    val process = Process(
      Seq(
        "python", "-m", "ipykernel", 
        "-f", CONNECTION_FILE_PATH
      )
    ).run(ProcessLogger(
      stdout => println(s"Kernel: $stdout"), 
      stderr => println(s"Kernel error: $stderr")
    ))
    
    // Wait for the connection file to be created
    var attempts = 0
    val maxAttempts = timeout * 2  // Check twice per second
    var connectionInfo: Option[Map[String, Json]] = None
    
    while (connectionInfo.isEmpty && attempts < maxAttempts) {
      if (connectionFile.exists() && connectionFile.length() > 0) {
        try {
          Thread.sleep(500) // Give the kernel time to finish writing the file
          val connInfoResult = parse(
            Source.fromFile(connectionFile).getLines().mkString
          ).flatMap(_.as[Map[String, Json]])
          
          connectionInfo = connInfoResult.toOption
        } catch {
          case e: Exception => 
            println(s"Error reading connection file: ${e.getMessage}")
        }
      }
      
      if (connectionInfo.isEmpty) {
        Thread.sleep(500)
        attempts += 1
      }
    }
    
    if (connectionInfo.isEmpty) {
      process.destroy()
      throw new RuntimeException(s"Failed to start kernel within timeout of $timeout seconds")
    }
    
    // Register shutdown hook to clean up the kernel when the JVM exits
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        println("Shutting down IPython kernel (JVM exit)...")
        process.destroy()
        if (connectionFile.exists()) {
          connectionFile.delete()
        }
      }
    })
    
    println(s"Kernel started with connection file: $CONNECTION_FILE_PATH")
    
    // Create and return the kernel client
    new IPythonKernel(connectionInfo.get, Some(process), Some(connectionFile))
  }
}