#!/usr/bin/env swift
import AppKit
import Foundation

/*
CDXC:AndroidBranding 2026-05-17-16:42:
Fastlane screenshots and icon are release-facing assets. Generate deterministic
Ghostex Android marketplace images so the listing cannot fall back to upstream
Termux terminal screenshots while the live device E2E remains machine-specific.

CDXC:AndroidBranding 2026-05-17-20:13:
Store screenshots must match the shipped neutral macOS-style Android drawer,
not the earlier blue-slate concept palette. Use the same shell/input/accent
tokens as the app palette so Play listing screenshots preview the real product.
*/

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let imageRoot = root.appendingPathComponent("fastlane/metadata/android/en-US/images")
let screenshotsRoot = imageRoot.appendingPathComponent("phoneScreenshots")
try FileManager.default.createDirectory(at: screenshotsRoot, withIntermediateDirectories: true)

let width: CGFloat = 1080
let height: CGFloat = 2340

struct Palette {
    static let bg = color(0x181818)
    static let panel = color(0x1f1f1f)
    static let panelAlt = color(0x262626)
    static let input = color(0x0e0e0e)
    static let border = color(0xffffff, alpha: 0.20)
    static let text = color(0xfafafa)
    static let muted = color(0xb5b5b5)
    static let dim = color(0x8a8a8a)
    static let accent = color(0x7dd3fc)
    static let working = color(0xf59e0b)
    static let attention = color(0x22c55e)
    static let danger = color(0xff6b7d)
}

func color(_ hex: Int, alpha: CGFloat = 1) -> NSColor {
    NSColor(
        calibratedRed: CGFloat((hex >> 16) & 0xff) / 255,
        green: CGFloat((hex >> 8) & 0xff) / 255,
        blue: CGFloat(hex & 0xff) / 255,
        alpha: alpha
    )
}

func topRect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> NSRect {
    NSRect(x: x, y: height - y - h, width: w, height: h)
}

func fillRounded(_ rect: NSRect, radius: CGFloat, color: NSColor, stroke: NSColor? = nil) {
    let path = NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius)
    color.setFill()
    path.fill()
    if let stroke {
        stroke.setStroke()
        path.lineWidth = 1
        path.stroke()
    }
}

func drawText(_ text: String, x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat,
              size: CGFloat, weight: NSFont.Weight = .regular,
              color: NSColor = Palette.text, align: NSTextAlignment = .left) {
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = align
    paragraph.lineBreakMode = .byWordWrapping
    paragraph.lineSpacing = 5
    let attributes: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: size, weight: weight),
        .foregroundColor: color,
        .paragraphStyle: paragraph
    ]
    NSString(string: text).draw(in: topRect(x, y, w, h), withAttributes: attributes)
}

func drawHeader(_ title: String, _ subtitle: String) {
    drawText("Ghostex Android", x: 72, y: 72, w: 520, h: 56, size: 34, weight: .bold)
    drawText("Tailscale SSH  |  Ghostex CLI  |  ZMX", x: 72, y: 126, w: 650, h: 44, size: 22, color: Palette.accent)
    drawText(title, x: 72, y: 220, w: 850, h: 150, size: 46, weight: .bold)
    drawText(subtitle, x: 72, y: 384, w: 820, h: 108, size: 25, color: Palette.muted)
}

func drawPhoneChrome() {
    fillRounded(topRect(0, 0, width, height), radius: 0, color: Palette.bg)
    drawText("16:42", x: 72, y: 34, w: 160, h: 36, size: 25, weight: .semibold, color: Palette.muted)
    drawText("5G  86%", x: 820, y: 34, w: 190, h: 36, size: 25, weight: .semibold, color: Palette.muted, align: .right)
}

func card(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, title: String,
          body: String, accent: NSColor = Palette.accent) {
    fillRounded(topRect(x, y, w, h), radius: 24, color: Palette.panel, stroke: Palette.border)
    fillRounded(topRect(x, y, 8, h), radius: 4, color: accent)
    drawText(title, x: x + 32, y: y + 28, w: w - 64, h: 36, size: 24, weight: .bold)
    drawText(body, x: x + 32, y: y + 78, w: w - 64, h: h - 98, size: 20, color: Palette.muted)
}

func pill(_ text: String, x: CGFloat, y: CGFloat, w: CGFloat, active: Bool = false) {
    fillRounded(topRect(x, y, w, 62), radius: 18, color: active ? Palette.accent : Palette.panelAlt,
                stroke: active ? nil : Palette.border)
    drawText(text, x: x, y: y + 18, w: w, h: 28, size: 20, weight: .semibold,
             color: active ? Palette.bg : Palette.text, align: .center)
}

func screenshot(_ name: String, draw: () -> Void) throws {
    guard let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil,
                                        pixelsWide: Int(width),
                                        pixelsHigh: Int(height),
                                        bitsPerSample: 8,
                                        samplesPerPixel: 4,
                                        hasAlpha: true,
                                        isPlanar: false,
                                        colorSpaceName: .deviceRGB,
                                        bytesPerRow: 0,
                                        bitsPerPixel: 0) else {
        throw NSError(domain: "GhostexStoreAssets", code: 1)
    }
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    drawPhoneChrome()
    draw()
    NSGraphicsContext.restoreGraphicsState()
    guard let jpeg = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.92]) else {
        throw NSError(domain: "GhostexStoreAssets", code: 2)
    }
    try jpeg.write(to: screenshotsRoot.appendingPathComponent(name))
}

func generateIcon() throws {
    let size: CGFloat = 512
    guard let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil,
                                        pixelsWide: Int(size),
                                        pixelsHigh: Int(size),
                                        bitsPerSample: 8,
                                        samplesPerPixel: 4,
                                        hasAlpha: true,
                                        isPlanar: false,
                                        colorSpaceName: .deviceRGB,
                                        bytesPerRow: 0,
                                        bitsPerPixel: 0) else {
        throw NSError(domain: "GhostexStoreAssets", code: 3)
    }
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    fillRounded(NSRect(x: 0, y: 0, width: size, height: size), radius: 108, color: Palette.bg)
    fillRounded(NSRect(x: 54, y: 54, width: 404, height: 404), radius: 88, color: Palette.panel, stroke: Palette.border)
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = .center
    NSString(string: "Gx").draw(in: NSRect(x: 0, y: 206, width: size, height: 130), withAttributes: [
        .font: NSFont.systemFont(ofSize: 104, weight: .heavy),
        .foregroundColor: Palette.text,
        .paragraphStyle: paragraph
    ])
    fillRounded(NSRect(x: 166, y: 146, width: 180, height: 18), radius: 9, color: Palette.accent)
    fillRounded(NSRect(x: 346, y: 146, width: 42, height: 18), radius: 9, color: Palette.working)
    NSGraphicsContext.restoreGraphicsState()
    guard let png = bitmap.representation(using: .png, properties: [:]) else {
        throw NSError(domain: "GhostexStoreAssets", code: 4)
    }
    try png.write(to: imageRoot.appendingPathComponent("icon.png"))
}

try screenshot("1.jpg") {
    drawHeader("Connect to your running Mac sessions",
               "Open Ghostex Android, add your Mac over Tailscale SSH, and continue the sessions already persisted by ZMX.")
    card(72, 540, 936, 168, title: "1. Tailscale on both devices",
         body: "Join the same tailnet on your Mac and phone, then use the Mac's Tailscale host or IP.")
    card(72, 736, 936, 168, title: "2. Remote Login and CLI",
         body: "Enable macOS Remote Login and verify `command -v ghostex && command -v zmx` from the SSH login shell.",
         accent: Palette.working)
    card(72, 932, 936, 168, title: "3. Add machine once",
         body: "Save host, username, port, and optional password. Ghostex reconnects to the last selected Mac on reopen.")
    pill("Open Tailscale", x: 72, y: 1160, w: 272)
    pill("Add machine", x: 374, y: 1160, w: 272, active: true)
    pill("Done", x: 676, y: 1160, w: 190)
}

try screenshot("2.jpg") {
    drawHeader("Manage multiple SSH machines",
               "Switch between Macs or accounts from one polished drawer, with saved-password and session-only credential modes.")
    card(72, 470, 936, 190, title: "Studio Mac  |  madda@studio.tailnet:22",
         body: "Selected now  |  Last connected 2m ago  |  Saved password enabled")
    pill("Connect", x: 110, y: 592, w: 190, active: true)
    pill("Password", x: 324, y: 592, w: 190)
    pill("Edit", x: 538, y: 592, w: 150)
    pill("More", x: 710, y: 592, w: 150)
    card(72, 712, 936, 176, title: "Office Mac  |  madda@office.tailnet:22",
         body: "Uses SSH keys, Tailscale SSH, or a session-only password", accent: Palette.working)
    card(72, 934, 936, 152, title: "Check connection",
         body: "Verify SSH reachability, Ghostex CLI, zmx, and host-key state before switching.")
}

try screenshot("3.jpg") {
    drawHeader("A macOS-style Ghostex sidebar",
               "The Android drawer mirrors projects, active session state, recency, provider metadata, and long-press actions.")
    fillRounded(topRect(72, 462, 936, 730), radius: 28, color: Palette.panel, stroke: Palette.border)
    drawText("Ghostex Android", x: 112, y: 500, w: 420, h: 38, size: 28, weight: .bold)
    pill("Studio Mac", x: 112, y: 558, w: 260, active: true)
    drawText("zmux", x: 112, y: 654, w: 280, h: 32, size: 22, weight: .bold, color: Palette.attention)
    card(112, 700, 856, 126, title: "Working  |  Ghostex Android release",
         body: "Ghostex  |  2m ago  |  zmx-main  |  codex")
    card(112, 848, 856, 126, title: "Needs review  |  CLI stable ids",
         body: "Ghostex  |  11m ago  |  zmx-main  |  agent", accent: Palette.working)
    drawText("Ungrouped", x: 112, y: 1010, w: 280, h: 32, size: 22, weight: .bold, color: Palette.muted)
    card(112, 1054, 856, 108, title: "Warm attach session",
         body: "Last seven clicked sessions stay warm per machine.", accent: Palette.accent)
}

try screenshot("4.jpg") {
    drawHeader("Touch-first controls for remote sessions",
               "Hover actions from macOS move into centered long-press menus with safe destructive confirmations.")
    fillRounded(topRect(132, 510, 816, 656), radius: 32, color: Palette.panel, stroke: Palette.border)
    drawText("Session actions", x: 172, y: 554, w: 520, h: 46, size: 32, weight: .bold)
    card(172, 634, 736, 96, title: "Attach",
         body: "Open ghostex attach --session-id in the terminal.")
    card(172, 750, 736, 96, title: "Focus on Mac",
         body: "Ask Ghostex CLI to focus the same stable session id.", accent: Palette.working)
    card(172, 866, 736, 96, title: "Rename",
         body: "Pass the new title through structured CLI flags.")
    card(172, 982, 736, 104, title: "Kill session",
         body: "Confirm before changing the remote ZMX lifecycle.", accent: Palette.danger)
    pill("Cancel", x: 172, y: 1108, w: 180)
}

try generateIcon()
