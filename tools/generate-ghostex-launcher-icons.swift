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

CDXC:AndroidBranding 2026-05-20-20:46:
Android launchers shrink legacy bitmap icons and adaptive icons reserve an
outer motion area, so transparent macOS icon padding makes Ghostex look too
small in the app drawer. Crop the transparent source bounds, regenerate the
legacy PNGs larger, and restore adaptive icon resources so the desktop mark
fills the Android launcher shape without relying on launcher legacy treatment.

CDXC:AndroidBranding 2026-05-21-02:36:
The Android launcher should show the full macOS Ghostex icon instead of a
zoomed crop. Draw the full source icon on a white padded square for legacy PNGs
and adaptive foregrounds so the app drawer preserves the complete desktop mark
while still giving Android masks a clean bright border.
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

func alphaBounds(in source: NSImage) throws -> NSRect {
    guard let cgImage = source.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
        throw NSError(domain: "GhostexLauncherIcon", code: 4)
    }
    let width = cgImage.width
    let height = cgImage.height
    let bytesPerPixel = 4
    let bytesPerRow = width * bytesPerPixel
    var pixels = [UInt8](repeating: 0, count: height * bytesPerRow)
    guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
          let context = CGContext(data: &pixels,
                                  width: width,
                                  height: height,
                                  bitsPerComponent: 8,
                                  bytesPerRow: bytesPerRow,
                                  space: colorSpace,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
        throw NSError(domain: "GhostexLauncherIcon", code: 5)
    }
    context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1

    for y in 0..<height {
        for x in 0..<width {
            let alpha = pixels[(y * bytesPerRow) + (x * bytesPerPixel) + 3]
            guard alpha > 2 else { continue }
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }
    }

    if maxX < minX || maxY < minY {
        return NSRect(origin: .zero, size: source.size)
    }

    let scaleX = source.size.width / CGFloat(width)
    let scaleY = source.size.height / CGFloat(height)
    return NSRect(x: CGFloat(minX) * scaleX,
                  y: CGFloat(minY) * scaleY,
                  width: CGFloat(maxX - minX + 1) * scaleX,
                  height: CGFloat(maxY - minY + 1) * scaleY)
}

func resizedIcon(from source: NSImage,
                 sourceBounds: NSRect,
                 size: Int,
                 insetFraction: CGFloat,
                 backgroundColor: NSColor = .clear) throws -> Data {
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
    backgroundColor.setFill()
    NSRect(x: 0, y: 0, width: size, height: size).fill()
    let inset = CGFloat(size) * insetFraction
    source.draw(in: NSRect(x: inset,
                           y: inset,
                           width: CGFloat(size) - (inset * 2),
                           height: CGFloat(size) - (inset * 2)),
                from: sourceBounds,
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

let macIconBounds = NSRect(origin: .zero, size: macIcon.size)
let iconBackground = NSColor.white

for density in densities {
    let directory = root.appendingPathComponent("app/src/main/res/\(density.directory)")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let png = try resizedIcon(from: macIcon,
                              sourceBounds: macIconBounds,
                              size: density.size,
                              insetFraction: 0.08,
                              backgroundColor: iconBackground)
    try png.write(to: directory.appendingPathComponent("ic_launcher.png"))
    try png.write(to: directory.appendingPathComponent("ic_launcher_round.png"))
}

let adaptiveDirectory = root.appendingPathComponent("app/src/main/res/mipmap-anydpi-v26")
try FileManager.default.createDirectory(at: adaptiveDirectory, withIntermediateDirectories: true)
let adaptiveIcon = """
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ghostex_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
try adaptiveIcon.write(to: adaptiveDirectory.appendingPathComponent("ic_launcher.xml"), atomically: true, encoding: .utf8)
try adaptiveIcon.write(to: adaptiveDirectory.appendingPathComponent("ic_launcher_round.xml"), atomically: true, encoding: .utf8)

let adaptiveForegroundDirectory = root.appendingPathComponent("app/src/main/res/drawable-nodpi")
try FileManager.default.createDirectory(at: adaptiveForegroundDirectory, withIntermediateDirectories: true)
try resizedIcon(from: macIcon,
                sourceBounds: macIconBounds,
                size: 432,
                insetFraction: 0.14,
                backgroundColor: iconBackground)
    .write(to: adaptiveForegroundDirectory.appendingPathComponent("ic_launcher_foreground.png"))

let fastlaneIconDirectory = root.appendingPathComponent("fastlane/metadata/android/en-US/images")
try FileManager.default.createDirectory(at: fastlaneIconDirectory, withIntermediateDirectories: true)
try resizedIcon(from: macIcon,
                sourceBounds: macIconBounds,
                size: 512,
                insetFraction: 0.08,
                backgroundColor: iconBackground)
    .write(to: fastlaneIconDirectory.appendingPathComponent("icon.png"))
