import org.joda.time.DateTime

class FilehandlerController {
   // BE CAREFUL, the values below must be match with the 
   // client parameters
   def tmpconfigfile = "config.cfg"
   def tmpdirprefix = "/tmp"

   def save = {
      // Creating the temporary directory
      def configtext = request.getFile(tmpconfigfile).inputStream.text
      def id = util.Util.getlinestartingwith(configtext,"\n","id").split("=")[1]
      def tmpdir = new File("${tmpdirprefix}/${id}")
      def flag = tmpdir.mkdir()
      println "[FilehandlerController - save(${new DateTime().toLocalTime()})] Directory ${tmpdir.getAbsolutePath()} created? -> ${flag}"
      if (!flag) {
         return
      }
      // Saving the sent files
      request.getFileNames().each {
         if (it == tmpconfigfile) {
            return
         }
         InputStream data = request.getFile(it).inputStream
         File datafile = new File("${tmpdir.getAbsolutePath()}/${it}")
         flag = util.Util.copiar(data as InputStream,datafile as File)
         println "[FilehandlreController] Did the '${tmpdir.getAbsolutePath()}/${it}' file copy? ${flag}"
      }
   }
}
