Change Log
==========

Version 4.0.0 *(2020-09-28)*
----------------------------

Update
- Compile & Target SDK Version 28 -> 30
- minSdkVersion -> 21

Feature
- [NEW] Get the bitmap directly.
  - `Blurry#get`, `Blurry#getAsync`
- [BREAKING CHANGES] Remove `async(ImageComposerListener)`.  Use getAsync(BlurTask.Callback) instead that.

Version 3.0.0 *(2018-11-19)*
----------------------------

Update:
- Migrate to AndroidX
- Remove novoda-bintray-plugin
- Fix some bugs

Version 2.1.1 *(2017-03-21)*
----------------------------

Update:
- Support Library 24.2.0 -> 25.3.0

Bug Fix:
- [Destroy Allocation and ScriptInstrinsicBlur objects to prevent memory leaks #51](https://github.com/wasabeef/Blurry/pull/51)
 

Version 2.1.0 *(2016-12-20)*
----------------------------

Update:
- Build Tools 24.0.2 -> 25.0.2
- Support Library 23.4.0 -> 24.2.0

PR:
- [#44](https://github.com/wasabeef/Blurry/pull/44) Implement BitmapComposer


Version 2.0.3 *(2016-08-04)*
----------------------------

Update:
- Build Tools 23.0.1 -> 24.0.2
- Support Library 23.0.1 -> 23.4.0

Version 2.0.2 *(2016-04-21)*
----------------------------

Fix Fatal Signal

Version 2.0.1 *(2016-04-21)*
----------------------------

Fix RSInvalidStateException.

Version 2.0.0 *(2016-03-02)*
----------------------------

Say v8.RenderScript goodbye

Version 1.1.0 *(2016-02-28)*
----------------------------

PR: [#21](https://github.com/wasabeef/Blurry/pull/21) Use FastBlur as a fallback upon RenderScript failure.  
　　fix Issue [#16](https://github.com/wasabeef/Blurry/issues/16)  
PR: [#18](https://github.com/wasabeef/Blurry/pull/18) Added optional listener.

Version 1.0.5 *(2015-11-27)*
----------------------------

Change the renderscriptTargetApi down to 20.  
 Warning:Renderscript support mode is not currently supported with renderscript target 21+  

Version 1.0.4 *(2015-09-07)*
----------------------------

fix leak code.

Version 1.0.3 *(2015-09-04)*
----------------------------

fix [#2](https://github.com/wasabeef/Blurry/issues/5) renderscript in build-tools.

Version 1.0.2 *(2015-09-02)*
----------------------------

Replace AsyncTask.

Version 1.0.1 *(2015-08-26)*
----------------------------

fix [#5](https://github.com/wasabeef/Blurry/issues/5) throws RuntimeException.


Version 1.0.0 *(2015-07-28)*
----------------------------

Refactor delete method.
Added Animation.

Version 0.0.3 *(2015-07-25)*
----------------------------

Asynchronous Support.

Version 0.0.2 *(2015-07-25)*
----------------------------

Refactor.

Version 0.0.1 *(2015-07-24)*
----------------------------

Initial release.
