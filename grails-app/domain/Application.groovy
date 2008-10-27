class Application {
   static belongsTo = [ Gridresource ]

   Gridresource gridresource
   String installationpath
   String datapath
   String name
   String executablename
   Date installationdate

   static constraints = {
      installationpath(nullable: false)
      datapath(nullable: false)
      name(nullable: false)
      gridresource(nullable: false)
      executablename(nullable:false)
      Date installationdate(nullable:false)
   }

}
