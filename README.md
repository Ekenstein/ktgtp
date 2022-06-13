# ktgtp
Once again a Go-related kotlin project. This project enables the user to communicate synchronously with a GTP engine, e.g. GnuGo, Leela Zero and what not.

### Get started
```kotlin
fun main() {
   // It's very simple to start an engine. The following example shows how to start GNU Go.
   // Note that this example requires you to have GNU Go in your PATH.
   gtpConsole("gnugo", "--mode", "gtp") {
      // in this scope you can interact with the engine in a synchronous way. 
      // E.g. sending a command will wait for a response.
      boardSize(19)
      komi(6.5)
      
      // Each of these commands will return a response in form of a GtpResponse which represents either
      // a successful response or a failure.
      val response = play(GtpValue.black, GtpValue.point(3, 3))
      if (!response.isSuccess()) {
         error("Failed to play the move, due to ${response.message}.")
      }
      
      // Responses can also contain a response value. In this case
      // the value of the response is a list of strings.
      val commands = listCommands()
      if (commands.isSuccess()) {
         commands.value.forEach { command ->
            // ¯\_(ツ)_/¯
         }
      }
      
      // Some engines also contains some special commands that might be specific for them, in that case you
      // can construct your own command and send it.
      val command = GtpCommand("i_am_happy", GtpValue.Bool(true))
      
      // Note the kotlin time construction here. Since we
      // are talking I/O here we can't really guarantee that we always get an answer.
      // In that case we can interrupt the command after a certain amount of time, in this case after 1 second.
      val someValue: Int? = send(command, 1.seconds).flatMap {
         // and since you are constructing your own command here, maybe you know how to convert the response
         // from a string to a more user-friendly type?
         it.toInt()
      }.getOrNull() // if you can't be bothered with the response type, just get the value or null.
   }
}
```

### But... if I don't want to have my GTP engine in the PATH?
```kotlin
fun main() {
   // well, there are some ways you can get around that
   gtpConsole(Path.of("/path/to/your/gnugo"), "--mode", "gtp") {
       // is one way
   }
    
   // or maybe you want to talk TCP/IP?
   gtpConsole("127.0.0.1", 1337) {
       // \o/
   }
   
   // or if you want to fiddle around yourself by creating a process and all that kind of stuff
   // you can always do;
   val process = ProcessBuilder(...).start()
   PipedGtpConsole(process).use {
       it.listCommands()
   }
}
```
