dataSource {
	pooled = true
	driverClassName = "org.postgresql.Driver"
	username = "jas"
	password = "pqaldns"
}
hibernate {
    cache.use_second_level_cache=true
    cache.use_query_cache=true
    cache.provider_class='com.opensymphony.oscache.hibernate.OSCacheProvider'
}
// environment specific settings
environments {
	development {
		dataSource {
			dbCreate = "create-drop" // one of 'create', 'create-drop','update'
	      driverClassName = "org.hsqldb.jdbcDriver"
			url = "jdbc:hsqldb:mem:devDB"
         username = "sa"
         password = ""
		}
	}
	test {
		dataSource {
			dbCreate = "create-drop"
			url = "jdbc:postgresql:gjtest"
		}
	}
	production {
		dataSource {
			dbCreate = "update"
			url = "jdbc:postgresql:gridjobs"
		}
	}
}
