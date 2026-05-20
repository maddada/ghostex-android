#!/usr/bin/env swift
import AppKit
import Foundation

/*
CDXC:AndroidBranding 2026-05-17-20:17:
Legacy launcher PNGs are still used below Android 8 where adaptive icons are
ignored. Generate them from the same neutral Ghostex terminal mark as the
adaptive foreground so older launchers do not show stale Termux or concept
branding.

CDXC:AndroidBranding 2026-05-20-18:30:
The Android launcher icon must match the desktop macOS Ghostex app icon.
Generate every Android launcher density and the Fastlane icon from the macOS
AppIcon 1024px source instead of maintaining a separate mobile terminal glyph.
*/

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let macIconSource = root
    .deletingLastPathComponent()
    .deletingLastPathComponent()
    .appendingPathComponent("native/macos/ghostexHost/Resources/Assets.xcassets/AppIcon.appiconset/icon_512x512@2x.png")
let densities: [(directory: String, size: Int)] = [
    ("mipmap-mdpi", 48),
    ("mipmap-hdpi", 72),
    ("mipmap-xhdpi", 96),
    ("mipmap-xxhdpi", 144),
    ("mipmap-xxxhdpi", 192)
]

func resizedIcon(from source: NSImage, size: Int) throws -> Data {
    guard let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil,
                                        pixelsWide: size,
                                        pixelsHigh: size,
                                        bitsPerSample: 8,
                                        samplesPerPixel: 4,
                                        hasAlpha: true,
                                        isPlanar: false,
                                        colorSpaceName: .deviceRGB,
                                        bytesPerRow: 0,
                                        bitsPerPixel: 0) else {
        throw NSError(domain: "GhostexLauncherIcon", code: 1)
    }
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    NSColor.clear.setFill()
    NSRect(x: 0, y: 0, width: size, height: size).fill()
    source.draw(in: NSRect(x: 0, y: 0, width: size, height: size),
                from: NSRect(origin: .zero, size: source.size),
                operation: .sourceOver,
                fraction: 1)
    NSGraphicsContext.restoreGraphicsState()
    guard let png = bitmap.representation(using: .png, properties: [:]) else {
        throw NSError(domain: "GhostexLauncherIcon", code: 2)
    }
    return png
}

guard let macIcon = NSImage(contentsOf: macIconSource) else {
    throw NSError(domain: "GhostexLauncherIcon", code: 3, userInfo: [
        NSLocalizedDescriptionKey: "Could not load macOS app icon at \(macIconSource.path)"
    ])
}

for density in densities {
    let directory = root.appendingPathComponent("app/src/main/res/\(density.directory)")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let png = try resizedIcon(from: macIcon, size: density.size)
    try png.write(to: directory.appendingPathComponent("ic_launcher.png"))
    try png.write(to: directory.appendingPathComponent("ic_launcher_round.png"))
}

let fastlaneIconDirectory = root.appendingPathComponent("fastlane/metadata/android/en-US/images")
try FileManager.default.createDirectory(at: fastlaneIconDirectory, withIntermediateDirectories: true)
try resizedIcon(from: macIcon, size: 512).write(to: fastlaneIconDirectory.appendingPathComponent("icon.png"))
