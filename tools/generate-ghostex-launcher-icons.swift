#!/usr/bin/env swift
import AppKit
import Foundation

/*
CDXC:AndroidBranding 2026-05-17-20:17:
Legacy launcher PNGs are still used below Android 8 where adaptive icons are
ignored. Generate them from the same neutral Ghostex terminal mark as the
adaptive foreground so older launchers do not show stale Termux or concept
branding.
*/

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let densities: [(directory: String, size: Int)] = [
    ("mipmap-mdpi", 48),
    ("mipmap-hdpi", 72),
    ("mipmap-xhdpi", 96),
    ("mipmap-xxhdpi", 144),
    ("mipmap-xxxhdpi", 192)
]

struct Palette {
    static let bg = color(0x181818)
    static let panel = color(0x1f1f1f)
    static let border = color(0xffffff, alpha: 0.20)
    static let text = color(0xfafafa)
    static let accent = color(0x7dd3fc)
    static let working = color(0xf59e0b)
}

func color(_ hex: Int, alpha: CGFloat = 1) -> NSColor {
    NSColor(
        calibratedRed: CGFloat((hex >> 16) & 0xff) / 255,
        green: CGFloat((hex >> 8) & 0xff) / 255,
        blue: CGFloat(hex & 0xff) / 255,
        alpha: alpha
    )
}

func fillRounded(_ rect: NSRect, radius: CGFloat, color: NSColor, stroke: NSColor? = nil, lineWidth: CGFloat = 1) {
    let path = NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius)
    color.setFill()
    path.fill()
    if let stroke {
        stroke.setStroke()
        path.lineWidth = lineWidth
        path.stroke()
    }
}

func drawIcon(size: Int) throws -> Data {
    let canvas = CGFloat(size)
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

    fillRounded(NSRect(x: 0, y: 0, width: canvas, height: canvas),
                radius: canvas * 0.22,
                color: Palette.bg)
    let terminalRect = NSRect(x: canvas * 0.19, y: canvas * 0.22,
                              width: canvas * 0.62, height: canvas * 0.56)
    fillRounded(terminalRect, radius: canvas * 0.09, color: Palette.panel,
                stroke: Palette.border, lineWidth: max(1, canvas * 0.006))
    fillRounded(NSRect(x: terminalRect.minX, y: terminalRect.maxY - canvas * 0.13,
                       width: terminalRect.width, height: canvas * 0.13),
                radius: canvas * 0.05, color: Palette.accent)

    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = .center
    let glyphAttrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.monospacedSystemFont(ofSize: canvas * 0.28, weight: .bold),
        .foregroundColor: Palette.text,
        .paragraphStyle: paragraph
    ]
    NSString(string: ">").draw(in: NSRect(x: terminalRect.minX + canvas * 0.04,
                                          y: terminalRect.minY + canvas * 0.18,
                                          width: canvas * 0.20,
                                          height: canvas * 0.22),
                               withAttributes: glyphAttrs)
    fillRounded(NSRect(x: terminalRect.minX + canvas * 0.36,
                       y: terminalRect.minY + canvas * 0.21,
                       width: canvas * 0.24,
                       height: max(2, canvas * 0.045)),
                radius: canvas * 0.02,
                color: Palette.working)

    NSGraphicsContext.restoreGraphicsState()
    guard let png = bitmap.representation(using: .png, properties: [:]) else {
        throw NSError(domain: "GhostexLauncherIcon", code: 2)
    }
    return png
}

for density in densities {
    let directory = root.appendingPathComponent("app/src/main/res/\(density.directory)")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let png = try drawIcon(size: density.size)
    try png.write(to: directory.appendingPathComponent("ic_launcher.png"))
    try png.write(to: directory.appendingPathComponent("ic_launcher_round.png"))
}
