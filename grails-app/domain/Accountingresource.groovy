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

   static constraints = {
      gridresource(nullable:false)
      initialtime(unique:true, nullable:false)
   }
}
