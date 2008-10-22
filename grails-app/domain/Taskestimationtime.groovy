class Taskestimationtime {
   static belongsTo = [ Gridresource ] 

   String state  // 'unsubmitted', 'pending', 'active', 'done', 'fail', 'error'
   long estimatedtime // value taken from probabilistic function
   long starttime // It contains the time when the task arrived to this state
                  // This field is also used as identifier
   boolean historic = false // if this record now it's historic
   boolean done = false // indicates if this task already traversed this
                        // state.
   long endtime // time when the task exits from 'state'

   // However, I have references 
   Rango rango
   Gridresource gridresource
   Task task
}
