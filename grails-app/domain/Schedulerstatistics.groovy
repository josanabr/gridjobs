class Schedulerstatistics {
   String schedulername
   long datetimems
   long elapsedtimems
   String parameters
   boolean succeeded

   static constraints = {
      schedulername(nullable:false)
      datetimems(nullable:false,unique:true)
      elapsedtimems(nullable:false)
      parameters(nullable:false)
   }
}
