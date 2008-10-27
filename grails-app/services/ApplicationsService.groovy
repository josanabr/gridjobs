class ApplicationsService 
implements remote.Applications
{

    boolean transactional = true
    static expose = [ 'hessian' ]

    String retrieveapplications() {
       println "[ApplicationService - retrieveapplications ${util.joda.Util.datetime()}]"
       def result = ""
       def criteria = Application.createCriteria()
       def allApplications = criteria.list {
          projections { 
             distinct('name')
          }
       }
       if (allApplications.size() == 0) {
          return result
       }
       int i = 0
       println "[ApplicationService - retrieveapplications]\t ${allApplications.size()} application(s) retrieved"
       for (; i < allApplications.size() - 1; i++) {
          result += "${allApplications[i]}|"
       }
       result += allApplications[i]
       println "[ApplicationService - retrieveapplications ${util.joda.Util.datetime()}] DONE"

       return result
    }

    String applicationattributes(String appname) {
       println "[ApplicationService - applicationattribute ${util.joda.Util.datetime()}] Retrieving attributes for application ${appname}"
       def result = ""

       def apps = Application.findAllByName(appname)

       if (apps.size() == 0)
          return result

       result = "${apps[0].name}|${apps[0].executablename}|${apps[0].installationpath}|${apps[0].datapath};"

       int i = 0
       for (; i < apps.size() - 1 ; i++) {
          result += "${apps[i].gridresource.name}|"
       }

       result += apps[i].gridresource.name
       println "[ApplicationService - applicationattribute ${util.joda.Util.datetime()}] Attributes for ${appname}: ${result} DONE"

       return result
    }
}
