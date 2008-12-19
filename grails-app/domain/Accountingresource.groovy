class Accountingresource {
   static belongsTo = Gridresource
   Gridresource gridresource 
   long initialtime // Despite of initialtime represents a Date object, for indexing purposes
                    // is better deal with it as a long number
   Date endtime
   boolean status // true -> succeed, false -> failed

   static constraints = {
      gridresource(nullable:false)
      initialtime(unique:true, nullable:false)
   }
}
