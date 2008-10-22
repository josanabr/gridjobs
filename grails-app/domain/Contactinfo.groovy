class Contactinfo {
   String firstname
   String lastname
   String email

   static constraints = {
      email(unique:true,nullable:false,email:true)
   }
}
