/*
 * This file is part of Semantic Version Stamp.
 * 
 * Semantic Version Stamp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Semantic Version Stamp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Semantic Version Stamp.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.prajnainc.semvers

import groovy.transform.TupleConstructor

import java.text.ParseException
import java.util.prefs.MacOSXPreferences;
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * VersionStamp
 *
 * A VersionStamp stamps some collection of artifacts with a semantic version number. Instances of this class can be compared, and implement
 * a total ordering that is as defined by Semantic Versioning 2.0.0-rc.1 defined at {@link http://semver.org/}
 * 
 * @author paul
 *
 */
@TupleConstructor
class VersionStamp implements Comparable<VersionStamp> {

	public final static String SEGMENT_CHARS = '0-9A-Za-z.'	// Character class of chars allowed in a version number, preRelease ID or build ID
	public final static String ELEMENT_CHARS = '0-9A-Za-z'	// Character class of chars allowed in a dot separated segment

	// A Pattern that verifies the format, and extracts major groups: version ID, and optional preRelese ID and build ID
	public final static String FORMAT_PATTERN = /\A([$SEGMENT_CHARS]+[$ELEMENT_CHARS])(-[$SEGMENT_CHARS]+[$ELEMENT_CHARS])*(\+[$SEGMENT_CHARS]+[$ELEMENT_CHARS])*\Z/
	public final static Pattern ELEMENT_PATTERN = ~/[$ELEMENT_CHARS]+/

	Integer major,minor,patch
	List preReleaseId
	List buildId

	/**
	 * ElementListComparator
	 *
	 * I am a Comparator that implements the ordering rules for optional list elements such as a pre-release Id or a 
	 * build Id. These have different priorities when one is missing, so the class can be configured to give a null
	 * element a lower or higher priority than a non-null element
	 * 
	 * Element lists are represented as lists of Strings or Integers. Unlike elements are compared natively, and unlike elements
	 * are compared using the Semantic Version rules (numeric elements have lower priority than alpha elements)
	 * 
	 * @author paul
	 *
	 */
	private static class ElementListComparator implements Comparator {

		// Tables to customize the priority result of a null<=> non-null comparison
		
		final static int NULL_LOWER_PRIORITY = 0	// Indicate null has a lower priority
		final static int NULL_HIGHER_PRIORITY = 1	// Indicate null has a lower priority

		/*
		 * Priority result list. Index into this with one of the above constants to get the
		 * correct return values for right and left non-null according to the given priority
		 */
		final static List PRIORITY_RESULTS = [
			[leftNonNull: 1, rightNonNull: -1],		// Null has lower priority results
			[leftNonNull: -1, rightNonNull: 1]		// Null has higher priority results
		]

		private int nullPriority

		// Convenience factory functions	
		public static ElementListComparator newNullHigherPriority() {
			return new ElementListComparator(NULL_HIGHER_PRIORITY)
		}
		
		public static ElementListComparator newNullLowerPriority() {
			return new ElementListComparator(NULL_LOWER_PRIORITY)
		}
		
		// Constructor
		ElementListComparator(int nullPriority) {
			this.nullPriority = nullPriority
		}

		/*
		 *  Comparison functions for elements - these are multi-method dispatched on the basis of
		 *  argument type - similar to a double-dispatch scheme
		 */
		private static int compareElements(String thisString, Integer otherInt) {
			// Numeric elements have lower priority, so I am greater than the other
			return 1
		}

		private static int compareElements(Integer thisInt, String otherString) {
			// Numeric elements have lower priority, so I am lesser than the other
			return -1
		}

		private static int compareElements(Integer thisInt, Integer otherInt) {
			// Compare numbers
			return thisInt.compareTo(otherInt)
		}

		private static int compareElements(String thisStr, String otherStr) {
			// Compare Strings
			return thisStr.compareTo(otherStr)
		}

		/* (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 * 
		 * The comparison function itself
		 */
		@Override
		public int compare(Object left, Object right) {
			return compareElementLists((List)left,(List)right)
		}

		private int compareElementLists(List left, List right) {
			if(left == null && right == null) {
				// Both are null - they are equal
				return 0
			} else if(left == null && right != null) {
				// Right operand is non-null - return priority result for rightNonNull
				return PRIORITY_RESULTS[nullPriority].rightNonNull
			} else if(left != null && right == null) {
				// Left operand is non-null - return priority result for leftNonNull
				return PRIORITY_RESULTS[nullPriority].leftNonNull
			} else if(left.size() != right.size()){
				return left.size().compareTo(right.size())
			}
			
			// Equal sized, non-null lists - compare each element
			for(int i = 0; i < left.size(); i++) {
				int result = compareElements(left[i],right[i])
				if(result != 0) {
					return result
				}
			}
			
			// They are equal
			return 0
		}
	}

	/**
	 * Parse the version ID elements and return them as a map of three elements
	 * 
	 * @param input
	 * @return
	 */
	private static Map parseVersion(String input) {
		Matcher m = input =~ /\A(\d+)\.(\d+)\.(\d)\Z/
		if (!m) {
			throw new ParseException("The version ID '$input' is not valid (X.Y.Z)",0)
		}
		def elements = m.lastMatcher[0][1..-1].collect { it as Integer }
		return [major: elements[0], minor: elements[1], patch: elements[2]]
	}

	private static List parseElement(String input) {
		return input.split(/\./).collect {
			it ==~ /\A\d+\Z/ ? it as Integer :it
		}
	}

	public static VersionStamp parse(String input) {
		Matcher m = input =~ FORMAT_PATTERN
		if(!m) {
			throw new ParseException("$input is not a valid semantic version string",0)
		}
		def elements = m.lastMatcher[0]
		String versionString = elements[1], releaseString = elements[2] ? elements[2][1..-1] : null, buildString = elements[3] ? elements[3][1..-1] : null
		Map ctorArgs = parseVersion(versionString)
		if(releaseString) {
			ctorArgs.preReleaseId = parseElement(releaseString)
		}
		if(buildString) {
			ctorArgs.buildId = parseElement(buildString)
		}
		return new VersionStamp(ctorArgs)
	}

	public static VersionStamp fromXml(Reader xmlReader) {
		Node dom = new XmlParser().parse(xmlReader)
		
		if(dom.name() != "version-stamp") {
			throw new ParseException("Unknown element: ${dom.name()}",0)
		}
		
		def attributes = dom.attributes()
		
		if(!attributes.keySet().containsAll(['major','minor','patch'])) {
			throw new ParseException("Node must contain all of major, minor, patch attributes")
		}
		
		Map ctorArgs = parseVersion([attributes.major,attributes.minor,attributes.patch].join("."))
		
		['preReleaseId','buildId'].each {
			if(attributes[it]) {
				ctorArgs[it] = parseElement(attributes[it])
			}
		}
		
		return new VersionStamp(ctorArgs)
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(VersionStamp other) {
		// Compare numeric version elements
		for(property in ['major','minor','patch']) {
			if(this[property] != other[property]) return this[property].compareTo(other[property])
		}

		// A preRelease has lower priority than an empty one, so a null result has higher priority
		def val = ElementListComparator.newNullHigherPriority().compare(preReleaseId,other.preReleaseId)
		if(val != 0) return val

		// A buildId has a higher priority than an empty one, so a null result has lower priority
		val = ElementListComparator.newNullLowerPriority().compare(buildId,other.buildId)
		if(val != 0) return val

		// We are equal
		return 0
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(VersionStamp obj) {
		return compareTo(obj) == 0;
	}

}
