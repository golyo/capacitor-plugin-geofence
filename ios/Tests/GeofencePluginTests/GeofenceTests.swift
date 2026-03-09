import XCTest
@testable import GeofencePlugin

class GeofenceTests: XCTestCase {
    func testEngineInitialization() {
        let engine = GeofenceEngine()
        XCTAssertNotNil(engine)
    }
}
