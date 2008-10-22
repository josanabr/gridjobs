import org.joda.time.DateTime

class DeployService implements remote.Deploy {

    boolean transactional = false
    static expose = ['hessian']

    String deploy(String user, String host, String appname, String appdir, String data, String basedir, String script, String app){
       println "[DeployService - ${new DateTime().toLocalTime()}]"
       def output = ""
       def tmpout = ""
       def destdir = "."
       // look for the gridresource
       def gr = Gridresource.findByName(host)
       def application = new Application(gridresource: gr, installationpath: appdir, datapath: data, name: appname)
       if (application.save() == null) {
          return "The application ${appname} can not be installed on ${user}@${host}"
       }

       def command = "scp ${basedir}/${script} ${user}@${host}:${destdir}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "")
          output += tmpout

       command = "scp ${basedir}/${app} ${user}@${host}:${destdir}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "")
          output += tmpout

       command = "ssh ${user}@${host} source ${script}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "")
          output += tmpout
       
       command = "ssh ${user}@${host} rm ${script}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "")
          output += tmpout

       command = "ssh ${user}@${host} rm ${app}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "" )
          output += tmpout
       return output
    }

   boolean checkapp(String appname) {
       def app = Application.findByName(appname)
       println "[DeployService - checkapp] "
       if (app == null)
          return false
       return true
   }

    boolean removeapp(String appname) {
       def applist = Application.findAllByName(appname) 
       if (applist == null)
          return false

       println "[DeployService - removeapp] "
       applist.each {
          it.delete()
       }
       return true
    }

}
