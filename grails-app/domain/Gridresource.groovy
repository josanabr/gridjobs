class Gridresource {
   static hasMany = [jobstates:Jobstate, task: Task, taskestimationtime: Taskestimationtime, application: Application, accounting: Accountingresource, characteristics: Resourcecharacteristics]

   String headnode // komolongma.ece.uprm.edu
   String name // komolongma.ece.uprm.edu
   String batchscheduler // sge. condor/pbs/lls
   String country // PR, US, JP,
   String organization // AIST, UPRM, SDC.
   String userhome 
   int sequence

   static constraints = {
      name(unique:true,nullable:false)
      sequence(unique:true,nullable:false)
   }
}
