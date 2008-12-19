class Task {
   static hasMany = [taskestimationtime: Taskestimationtime]
   static belongsTo = [Gridresource]

   long submittedtime
   long unsubmitted
   long pending
   long active
   long done

   String output
   String exitstatus // FAILED, DONE
   String state // UNSUBMITTED, PENDING, ACTIVE, DONE, ERROR, FAILED

   String parameters // execution parameters
   String function   // It's the name given to this execution

   Gridresource gridresource

   Date lastvisit // last time when the task status was queried
   Date nextvisit // next time when the task status will be checked

   static constraints = {
      submittedtime(unique: true, nullable: false)
      gridresource(nullable: false)

      unsubmitted(nullable: true)
      state(nullable: true)
      pending(nullable: true)
      active(nullable: true)
      done(nullable: true)
      output(nullable: true)
      exitstatus(nullable: true)
      parameters(nullable: true)
      function(nullable: true)
      lastvisit(nullable: true)
      nextvisit(nullable: true)
   }
}
