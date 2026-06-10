#import <Cocoa/Cocoa.h>

@interface AppDelegate : NSObject <NSApplicationDelegate>
@property(nonatomic, strong) NSStatusItem *statusItem;
@property(nonatomic, strong) NSMenuItem *statusMenuItem;
@property(nonatomic, copy) NSString *repoRoot;
@end

@implementation AppDelegate

- (void)applicationDidFinishLaunching:(NSNotification *)notification {
    self.repoRoot = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"AIToolsRepoRoot"];
    if (self.repoRoot.length == 0) {
        self.repoRoot = [[NSFileManager defaultManager] currentDirectoryPath];
    }

    [NSApp setActivationPolicy:NSApplicationActivationPolicyAccessory];
    self.statusItem = [[NSStatusBar systemStatusBar] statusItemWithLength:NSVariableStatusItemLength];
    self.statusItem.button.title = @"AITools";
    self.statusItem.button.toolTip = @"AITools Web";
    [self buildMenu];
    [self refreshStatus];
}

- (void)buildMenu {
    NSMenu *menu = [[NSMenu alloc] init];
    self.statusMenuItem = [[NSMenuItem alloc] initWithTitle:@"Checking..." action:nil keyEquivalent:@""];
    self.statusMenuItem.enabled = NO;
    [menu addItem:self.statusMenuItem];
    [menu addItem:[NSMenuItem separatorItem]];
    [menu addItem:[self itemWithTitle:@"Start and Open Web" action:@selector(startAndOpen)]];
    [menu addItem:[self itemWithTitle:@"Start" action:@selector(startWeb)]];
    [menu addItem:[self itemWithTitle:@"Stop" action:@selector(stopWeb)]];
    [menu addItem:[self itemWithTitle:@"Restart" action:@selector(restartWeb)]];
    [menu addItem:[NSMenuItem separatorItem]];
    [menu addItem:[self itemWithTitle:@"Open Web" action:@selector(openWebOnly)]];
    [menu addItem:[self itemWithTitle:@"Refresh Status" action:@selector(refreshStatusAction)]];
    [menu addItem:[NSMenuItem separatorItem]];
    [menu addItem:[self itemWithTitle:@"Quit" action:@selector(quit)]];
    self.statusItem.menu = menu;
}

- (NSMenuItem *)itemWithTitle:(NSString *)title action:(SEL)action {
    NSMenuItem *item = [[NSMenuItem alloc] initWithTitle:title action:action keyEquivalent:@""];
    item.target = self;
    return item;
}

- (void)startAndOpen {
    [self runWebctl:@"open"];
}

- (void)startWeb {
    [self runWebctl:@"start"];
}

- (void)stopWeb {
    [self runWebctl:@"stop"];
}

- (void)restartWeb {
    [self runWebctl:@"restart"];
}

- (void)openWebOnly {
    [[NSWorkspace sharedWorkspace] openURL:[NSURL URLWithString:@"http://127.0.0.1:8788/web/"]];
    [self refreshStatus];
}

- (void)refreshStatusAction {
    [self refreshStatus];
}

- (void)quit {
    [NSApp terminate:nil];
}

- (void)runWebctl:(NSString *)command {
    self.statusMenuItem.title = [NSString stringWithFormat:@"Running %@...", command];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        NSString *result = [self runScript:command];
        dispatch_async(dispatch_get_main_queue(), ^{
            self.statusMenuItem.title = [self firstLine:result];
            [self refreshStatus];
        });
    });
}

- (void)refreshStatus {
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_UTILITY, 0), ^{
        NSString *result = [self runScript:@"status"];
        BOOL running = [result containsString:@"running"];
        dispatch_async(dispatch_get_main_queue(), ^{
            self.statusItem.button.title = running ? @"AITools On" : @"AITools Off";
            self.statusMenuItem.title = [self firstLine:result];
        });
    });
}

- (NSString *)runScript:(NSString *)command {
    NSTask *task = [[NSTask alloc] init];
    task.currentDirectoryPath = self.repoRoot;
    task.launchPath = @"/bin/bash";
    task.arguments = @[@"scripts/webctl.sh", command];

    NSPipe *pipe = [NSPipe pipe];
    task.standardOutput = pipe;
    task.standardError = pipe;

    @try {
        [task launch];
        [task waitUntilExit];
        NSData *data = [[pipe fileHandleForReading] readDataToEndOfFile];
        NSString *output = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
        return output ?: @"";
    } @catch (NSException *exception) {
        return [NSString stringWithFormat:@"Failed: %@", exception.reason ?: @"Unknown error"];
    }
}

- (NSString *)firstLine:(NSString *)text {
    NSArray<NSString *> *lines = [text componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]];
    for (NSString *line in lines) {
        if (line.length > 0) return line;
    }
    return @"No output";
}

@end

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        NSApplication *app = [NSApplication sharedApplication];
        AppDelegate *delegate = [[AppDelegate alloc] init];
        app.delegate = delegate;
        [app run];
    }
    return 0;
}
