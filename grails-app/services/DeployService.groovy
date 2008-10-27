import org.joda.time.DateTime

class DeployService implements remote.Deploy {

    boolean transactional = false
    static expose = ['hessian']

    String deploy(String user, String host, String appname, String executablename, String appdir, String data, String basedir, String script, String app){
       println "[DeployService - ${new DateTime().toLocalTime()}]"
       def output = ""
       def tmpout = ""
       def destdir = "."
       // look for the gridresource
       def gr = Gridresource.findByName(host)
       def application = new Application(gridresource: gr, installationpath: appdir, executablename: executablename, installationdate: new DateTime().toDate(), datapath: data, name: appname)
       println "[DeployService - deploy ${util.joda.Util.datetime()}] Deploying application ${appname}"
       if (application.save() == null) { 
          return "The application ${appname} can not be installed on ${user}@${host}"
       } else {
          println "[DeployService - deploy (${appname})]\t Information saved to DB"
       }

       def command = "scp ${basedir}/${script} ${user}@${host}:${destdir}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "")
          output += tmpout
       println "[DeployService - deploy ${appname}]\t Script copied"

       command = "scp ${basedir}/${app} ${user}@${host}:${destdir}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "")
          output += tmpout
       println "[DeployService - deploy ${appname}]\t Application(${app}) copied"

       command = "ssh ${user}@${host} source ${script}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "")
          output += tmpout
       println "[DeployService - deploy ${appname}]\t Script(${script}) executed"
       
       command = "ssh ${user}@${host} rm ${script}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "")
          output += tmpout
       println "[DeployService - deploy ${appname}]\t Script file removed"

       command = "ssh ${user}@${host} rm ${app}"
       tmpout = util.Util.executegetoutput(command)
       if (tmpout != "\n" && tmpout != "" )
          output += tmpout
       println "[DeployService - deploy ${appname}]\t Application file removed"
       println "[DeployService - deploy ${util.joda.Util.datetime()}] Application ${appname} deployed!"
       return output
    }

   boolean checkapp(String appname) {
       def app = Application.findByName(appname)
       println "[DeployService - checkapp ${util.joda.Util.datetime()}] Application name ${appname}"
       if (app == null)
          return false
       return true
   }

    boolean removeapp(String appname) {
       def applist = Application.findAllByName(appname) 
       if (applist == null)
          return false

       println "[DeployService - removeapp ${util.joda.Util.datetime()}] Application name ${appname}"
       applist.each {
          it.delete()
       }
       return true
    }

}
