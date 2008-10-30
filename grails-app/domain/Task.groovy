class Task {
   static hasMany = [taskestimationtime: Taskestimationtime]
   static belongsTo = [Gridresource]

   long unsubmitted
   long pending
   long active
   long done
   String output
   String exitstatus // FAILED, DONE
   Gridresource gridresource
   String state // UNSUBMITTED, PENDING, ACTIVE, DONE, ERROR, FAILED

   static constraints = {
      unsubmitted(unique: true, nullable: false)
      gridresource(nullable: false)
      state(nullable, false)

      pending(nullable: true)
      active(nullable: true)
      done(nullable: true)
      output(nullable: true)
      exitstatus(nullable: true)
   }
}
