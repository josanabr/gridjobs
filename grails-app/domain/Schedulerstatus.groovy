class Schedulerstatus {
   int currentresource
   int sequence
   static constraints = {
      currentresource(unique:true)
   }
}
