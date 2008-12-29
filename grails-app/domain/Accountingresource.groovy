class Accountingresource {
   static belongsTo = Gridresource
   Gridresource gridresource 
   long initialtime // Despite of initialtime represents a Date object, for indexing purposes
                    // is better deal with it as a long number
   Date endtime
   // A value equals -1 indicates failure during its execution, 
   //                 0 means successful, 
   //                 1 indicates the resource were not available.
   int status // true -> succeed, false -> failed
   // This field can be infered. It contains how many tasks were 
   // assigned to 'gridresource' at the moment when this event
   // ocurred
   int tasksassigned

   static constraints = {
      gridresource(nullable:false)
      initialtime(unique:true, nullable:false)
      endtime(nullable:true)
      tasksassigned(nullable:true)
   }
}
