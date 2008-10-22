class FilemanagerService 
implements remote.FileManager 
{

    boolean transactional = false
    def mailService
    static expose = ['hessian']

   String query(String stresource, String dir) {
      def resource = Gridresource.findByName(stresource)
      if (resource == null) 
         return "Resource not found"
      def username = resource.userhome
      username = username.substring(username.lastIndexOf("/") + 1, username.size())
      def command = "ssh ${username}@${stresource} ls -l ${resource.userhome}/${dir}"
      def output = util.Util.executegetoutput(command)

      return output
   }

   String fetch(String stresource, String dir, String email) {
      def resource = Gridresource.findByName(stresource)
      if (resource == null)
         return "Resource not found"

      def username = resource.userhome
      username = username.substring(username.lastIndexOf("/") + 1, username.size())
      def command = "ssh ${username}@${stresource} cat ${resource.userhome}/${dir}"
      def output = util.Util.executegetoutput(command)

      mailService.sendMail {
         from "grid.jobs@gmail.com"
         to email
         subject "Content of file ${dir}"
         body output
      }

      return "Email sent"
   }

   String delete(String stresource, String dir) {
      def resource = Gridresource.findByName(stresource)
      if (resource == null)
         return "Resource not found"

      def username = resource.userhome
      username = username.substring(username.lastIndexOf("/") + 1, username.size())
      def command = "ssh ${username}@${stresource} rm -f ${resource.userhome}/${dir}"
      def output = util.Util.executegetoutput(command)

      return output
   }
}
