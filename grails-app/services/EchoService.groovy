class EchoService implements remote.Echo {

    boolean transactional = false
    static expose = ['hessian']
    //def mailService

   String echo(String _echo) {
     /* mailService.sendMail {
         to "s047267@ece.uprm.edu"
         subject "${_echo}"
         body "${_echo}"
      }
      */
      return _echo
   }
}
