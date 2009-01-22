import org.joda.time.DateTime

class BootStrap {
     def monitorService

     def init = { servletContext ->
      def starttime 
      def endtime

      def userhome = "/home/jas"
      def states = [ 'UNSUBMITTED', 'PENDING', 'ACTIVE', 'DONE' ] 

      starttime = new DateTime()
      // Defining the grid resources 
      def x 
      /*
      x = new Gridresource(name:'komolongma.ece.uprm.edu',
                               headnode:'komolongma.ece.uprm.edu',
                               batchscheduler: 'sge',
                               country: 'PR',
                               userhome: userhome,
                               organization: 'UPRM')
      x.save()
      */
      x = new Gridresource(name:'rocks-52.sdsc.edu',
                           headnode:'rocks-52.sdsc.edu',
                           batchscheduler: 'sge',
                           country: 'US',
                           userhome: userhome,
                           sequence: 1,
                           organization: 'SDSC')
      x.save()
      x = new Gridresource(name:'rocks-153.sdsc.edu',
                           headnode:'rocks-153.sdsc.edu',
                           batchscheduler: 'sge',
                           country: 'US',
                           userhome: userhome,
                           sequence: 2,
                           organization: 'SDSC')
      x.save()
      x = new Gridresource(name:'sakura.hpcc.jp',
                           headnode:'sakura.hpcc.jp',
                           batchscheduler: 'sge',
                           country: 'JP',
                           userhome: userhome,
                           sequence: 3,
                           organization: 'AIST')
      x.save()
      /*
      x = new Gridresource(name:'fsvc001.asc.hpcc.jp',
                           headnode:'fsvc001.asc.hpcc.jp',
                           batchscheduler: 'sge',
                           country: 'JP',
                           userhome: userhome,
                           sequence: 4,
                           organization: 'AIST')
      x.save()
      */

      endtime = new DateTime()
      println "[Bootstrap] Elapsed time during insertion of records into Gridresource table: ${endtime.millis - starttime.millis} ms."

      x = new Schedulerstatus(sequence:1, currentresource:1)
      x.save()
      // This second register enables the utilization of 
      // 'RrstoreschedService' class.
      // if 'currentresource' == 1; enable
      // otherwise; disable
      x = new Schedulerstatus(sequence:2, currentresource:1)
      x.save()

      starttime = new DateTime()
      
      Rango.list().each {
         it.delete()
      }

      endtime = new DateTime()

      println "[Bootstrap] Elapsed time during records deletion from Rango table: ${endtime.millis - starttime.millis} ms."

      //
      // Initializing 'Rango' table
      //

      def rangefile = new File('range.xml') // For more details about this file
                                         // visit grails-app/domain/Rango.groovy
      def xmlreader
      if (rangefile.exists()) {
         starttime = new DateTime()
         xmlreader = new XmlParser().parse(rangefile)
         xmlreader.item.each {
            def _range = new Rango(stvalue: it.'@stvalue',value: new Integer(it.'@value'))
            _range.save()
         }
         endtime = new DateTime()
         println "[Bootstrap] Elapsed time during insertion of records into Rango table: ${endtime.millis - starttime.millis} ms."

      } else {
         println "[Bootstrap] WARNING forecasting feature will not work due to 'range.xml' file doesn't exist"
      }

      def configfile = new File('config.xml')
      if (configfile.exists()) {
         xmlreader = new XmlParser().parse(configfile)
         xmlreader.contactinfo[0].contact.each {
            def contactinfo = new Contactinfo(firstname: it.'@firstname', lastname: it.'@lastname', email: it.'@email')
            contactinfo.save()
         }
      }

      //
      // Initializing 'Historicperformance' table
      //
      // WARNING if the 'range.xml' file is new then it's highly recommended
      // to force all records deletion from 'Historicperformance' table.
      //
      def gridresource = Gridresource.list()
      def range = Rango.list()
      gridresource.each { gs ->
         def nbench = "nbench"
         def newapplication = new Application(datapath: nbench, executablename: nbench, gridresource: gs, installationdate: new Date(), installationpath: nbench, name: nbench)
         if (newapplication.save() == null) {
            println "[Bootstrap] Error saving the application data"
            newapplication.allErrors.each {
               println "[Bootstrap] \t ${it.defaultMessage}"
            }
         }
         range.each { r ->
            states.each { state ->
               def hpforfinding = new Historicperformance(range: r.value, state: state, gridresourcename: gs.name)
               def hp = Historicperformance.find(hpforfinding)
               if (hp == null)  {
                  println "[Bootstrap] Adding new record to 'Historicperformance' table (${r.value},${state}, ${gs.name})"
                  hp = new Historicperformance(range: r.value, state: state, gridresourcename: gs.name)
                  hp.save()
               }
            }
         }
      }
      starttime = new DateTime()
      monitorService.monitorgridresources()
      endtime = new DateTime()
      println "[Bootstrap] Elapsed time during initial resource monitoring: ${endtime.millis - starttime.millis} ms."

     }
     def destroy = {
     }
} 
