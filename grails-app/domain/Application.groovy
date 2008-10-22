class Application {
   static belongsTo = [ Gridresource ]

   Gridresource gridresource
   String installationpath
   String datapath
   String name

   static constraints = {
      installationpath(nullable: false)
      datapath(nullable: false)
      name(nullable: false)
      gridresource(nullable: false)
   }

}
