class ApplicationsService 
implements remote.Applications
{

    boolean transactional = true
    static expose = [ 'hessian' ]

    String retrieveapplications() {
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
       println "[ApplicationService - retrieveapplications] ${allApplications.class.name}"
       for (; i < allApplications.size() - 1; i++) {
          result += "${allApplications[i]}|"
       }
       result += allApplications[i]

       return result
    }

    String applicationattributes(String appname) {
       def result = ""

       def apps = Application.findAllByName(appname)

       if (apps.size() == 0)
          return result

       result = "${apps[0].name}|${apps[0].installationpath}|${apps[0].datapath};"

       int i = 0
       for (; i < apps.size() - 1 ; i++) {
          result += "${apps[i].gridresource.name}|"
       }

       result += apps[i].gridresource.name

       return result
    }
}
