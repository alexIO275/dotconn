// Regenerate build/icon.png on macOS; mirrors the existing MicroCrew mark in icon.svg.
import AppKit
let size = NSSize(width: 1024, height: 1024)
let image = NSImage(size: size)
image.lockFocus()
NSColor.white.setFill()
NSBezierPath(roundedRect: NSRect(origin: .zero, size: size), xRadius: 220, yRadius: 220).fill()
let blue = NSColor(srgbRed: 40 / 255, green: 86 / 255, blue: 220 / 255, alpha: 1)
blue.setStroke()
let line = NSBezierPath()
line.move(to: NSPoint(x: 256, y: 640))
line.line(to: NSPoint(x: 512, y: 352))
line.line(to: NSPoint(x: 768, y: 640))
line.lineWidth = 48
line.lineCapStyle = .round
line.lineJoinStyle = .round
line.stroke()
blue.setFill()
for point in [NSPoint(x: 256, y: 768), NSPoint(x: 768, y: 768), NSPoint(x: 512, y: 256)] {
  NSBezierPath(ovalIn: NSRect(x: point.x - 82, y: point.y - 82, width: 164, height: 164)).fill()
}
image.unlockFocus()
let bitmap = NSBitmapImageRep(data: image.tiffRepresentation!)!
try bitmap.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
