class FilemanagerService 
implements remote.FileManager 
{

    boolean transactional = false
    def mailService
    static expose = ['hessian']

   // This metho is equivalent to 'ls -l'
   String query(String stresource, String dir) {
      println "[FilenamagerService - query ${util.joda.Util.datetime()}] Dir ${dir} on resource ${stresource}"
      def resource = Gridresource.findByName(stresource)
      if (resource == null) {
         println "[FilemanagerService - query ${util.joda.Util.datetime()}] Resource didn't found"
         return "Resource not found"
      }
      def username = resource.userhome
      username = username.substring(username.lastIndexOf("/") + 1, username.size())
      println "[FilenamagerService - query]\t Executing command 'ls -l ${resource.userhome}/${dir}'"
      def command = "ssh ${username}@${stresource} ls -l ${resource.userhome}/${dir}"
      def output = util.Util.executegetoutput(command)
      println "[FilenamagerService - query ${util.joda.Util.datetime()}]"

      return output
   }

   String fetch(String stresource, String dir, String email) {
      println "[FilemanagerService - fetch ${util.joda.Util.datetime()}] Resource ${stresource} Dir ${dir} Email ${email}"
      int chunksize = 500
      def resource = Gridresource.findByName(stresource)

      if (resource == null) {
         println "[FilemanagerService - fetch ${util.joda.Util.datetime()}] Resource didn't found"
         return "Resource not found"
      }

      def username = resource.userhome
      username = username.substring(username.lastIndexOf("/") + 1, username.size())
      println "[FilemanagerService - fetch]\t Retrieving content from ${resource.userhome}/${dir}"
      def command = "ssh ${username}@${stresource} cat ${resource.userhome}/${dir}"
      def output = util.Util.executegetoutput(command)
      def splitoutput = output.split("\n")
      int i = 0;
      int base = i * chunksize
      for ( ; base < splitoutput.size(); ) {
         output = ""
         int j = 0
         for (; j < chunksize && base + j < splitoutput.size(); j++) {
            output += splitoutput[base + j] + "\n"
         }
         println "[FilemanagerService - fetch]\t Sending part ${i + 1}"
         mailService.sendMail {
            from "grid.jobs@gmail.com"
            to email
            subject "Content of file ${dir} (part ${i + 1})"
            body output
         }
         i++
         base = i * chunksize
      }
      println "[FilemanagerService - fetch ${util.joda.Util.datetime()}] Resource ${stresource} Dir ${dir} Email ${email} DONE!"

      return "Email sent"
   }

   String delete(String stresource, String dir) {
      println "[FilemanagerService - delete ${util.joda.Util.datetime()}] Resource ${stresource} Dir ${dir}"
      def resource = Gridresource.findByName(stresource)
      if (resource == null) { 
         println "[FilemanagerService - delete ${util.joda.Util.datetime()}] Resource didn't found"
         return "Resource not found"
      }

      def username = resource.userhome
      username = username.substring(username.lastIndexOf("/") + 1, username.size())
      println "[FilemanagerService - delete]\t Deleting file ${dir}"
      def command = "ssh ${username}@${stresource} rm -f ${resource.userhome}/${dir}"
      def output = util.Util.executegetoutput(command)
      println "[FilemanagerService - delete ${util.joda.Util.datetime()}] Resource ${stresource} Dir ${dir} output -> ${output}"

      return output
   }
}
