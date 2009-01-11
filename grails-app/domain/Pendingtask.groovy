/**
This class is employed for the 'RrstoreschedService' class.
*/
class Pendingtask {
   Date initialtime // time when this entry was created
   String applicationname
   String parameters
   String executablename
   Date resumedtime
   boolean attended
   static constraints = {
      initialtime(nullable:false,unique:true)
      applicationname(nullable:false)
      parameters(nullable:false)
      executablename(nullable:false)
      attended(nullable:false)
      resumedtime(nullable:true)
   }
}
