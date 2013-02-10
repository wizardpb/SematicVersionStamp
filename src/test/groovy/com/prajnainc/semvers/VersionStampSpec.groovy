package com.prajnainc.semvers

import spock.lang.Specification

/**
 * VersionStampSpec
 *
 * <Description here>
 * 
 * @author paul
 *
 */
class VersionStampSpec extends Specification {

	def "parse"() {

		expect:
		VersionStamp v = VersionStamp.parse(versionString)
		v != null
		v.major == major
		v.minor == minor
		v.patch == patch
		v.preReleaseId == preRel
		v.buildId == buildId

		where:
		versionString			|| major	| minor	| patch | preRel		| buildId
		'1.0.0'					|| 1		| 0		| 0		| null			| null
		'1.0.0-rc.1'			|| 1		| 0		| 0		| ['rc',1]		| null
		'1.0.0-rc.1+build.1.0'	|| 1		| 0		| 0		| ['rc',1]		| ['build',1,0]
	}

	def "parse XML stream"() {
		
		String xml = "<version-stamp major='1' minor='0' patch='0' preReleaseId='rc.2' buildId='build.1.2'/>"
		
		when:
		VersionStamp v = VersionStamp.fromXml(new StringReader(xml))
		
		then:
		v.major == 1
		v.minor == 0
		v.patch == 0
		v.preReleaseId == ['rc',2]
		v.buildId == ['build',1,2]
	}
	
	def "equality"() {

		expect:
		VersionStamp v1 = VersionStamp.parse(versionString)
		VersionStamp v2 = VersionStamp.parse(versionString)
		v1 == v2
		v1.equals(v2)

		where:
		versionString << [
			'1.0.0',
			'1.0.0-rc.1',
			'1.0.0-rc.1+build.1.0'
		]

	}

	def "inequality"() {

		expect:
		VersionStamp v1 = VersionStamp.parse(v1Str)
		VersionStamp v2 = VersionStamp.parse(v2Str)
		v1 != v2
		! v1.equals(v2)

		where:
		v1Str 					| v2Str
		'1.0.0'					| '2.0.0'
		'1.0.0-rc.1'			| '1.0.0-rc.2'
		'1.0.0-rc.1+build.1.0'	| '1.0.0-rc.1+build.2.0'
	}

	def "greater than"() {

		expect:
		VersionStamp v1 = VersionStamp.parse(v1Str)
		VersionStamp v2 = VersionStamp.parse(v2Str)
		v1 < v2

		where:
		v1Str 					| v2Str
		'1.0.0'					| '1.0.1'
		'1.0.0'					| '1.1.0'
		'1.0.0'					| '1.1.0'
		'1.0.0-rc1'				| '1.0.0'
		'1.0.0-rc1+buildId.1'	| '1.0.0'
		'1.0.0-rc1+buildId.1'	| '1.0.0+buildId'
		'1.0.0-rc1'				| '1.0.0-rc2'
		'1.0.0-rc.1'			| '1.0.0-rc.2'
		'1.0.0-rc1+buildId'		| '1.0.0-rc2'
		'1.0.0-rc.1+buildId'	| '1.0.0-rc.2'
		'1.0.0'					| '1.1.0+buildId'
		'1.0.0-rc1'				| '1.1.0-rc1+buildId'
		'1.0.0-rc1+buildId.1'	| '1.1.0-rc1+buildId.1'
	}


}
