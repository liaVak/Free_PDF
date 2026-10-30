package ru.ilyavakarin.pdfreader

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderApp(model: ReaderViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val recents by model.recents.collectAsStateWithLifecycle()
    val document=state.document
    val context=LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let {model.open(it)} }
    val openFile:()->Unit={picker.launch(arrayOf("application/pdf"))}
    val snack=remember {SnackbarHostState()}
    LaunchedEffect(state.error) {
        state.error?.let { snack.showSnackbar(it,duration=SnackbarDuration.Long);model.dismissError() }
    }
    BackHandler(state.document!=null || state.loading) {model.close()}
    Scaffold(snackbarHost={SnackbarHost(snack)}) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    CircularProgressIndicator()
                    Text("Открываем документ…")
                    TextButton(onClick=model::close) {Text("Отмена")}
                }
                document!=null -> key(document) {
                    ReaderScreen(document,state.sharing,openFile,model::close,
                        {model.share {context.startActivity(it)}},
                        {page,fraction->model.position(document,page,fraction)},model::persistPosition)
                }
                else -> LibraryScreen(recents,openFile,{model.open(Uri.parse(it.uri),false)},model::remove)
            }
        }
    }
}

@Composable
private fun LibraryScreen(documents:List<RecentDocument>,open:()->Unit,read:(RecentDocument)->Unit,remove:(String)->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                Icon(painterResource(R.drawable.ic_pdf),null,Modifier.padding(14.dp).size(28.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column {
                Text("PDF Reader",style=MaterialTheme.typography.headlineMedium)
                Text("На вашем устройстве",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick=open,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(16.dp)) {
            Icon(painterResource(R.drawable.ic_folder_open),null)
            Spacer(Modifier.width(10.dp))
            Text("Открыть PDF",style=MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("Недавние",style=MaterialTheme.typography.titleLarge)
            if(documents.isNotEmpty()) Text("${documents.size} / 20",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        if(documents.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Icon(painterResource(R.drawable.ic_document),null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(18.dp))
                Text("Начните с документа",style=MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Открытые PDF появятся здесь.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Место чтения сохранится автоматически.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)) {
            items(documents,key={it.uri}) {doc ->
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.clickable {read(doc)}.padding(start=16.dp,top=14.dp,bottom=14.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_document),null,tint=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(5.dp))
                            val date=remember(doc.openedAt) {DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(doc.openedAt))}
                            Text("Стр. ${doc.page+1} · $date",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick={remove(doc.uri)}) {Icon(painterResource(R.drawable.ic_close),"Убрать ${doc.name} из истории")}
                    }
                }
            }
        }
    }
}

private data class Anchor(val page:Int,val fraction:Float)
private data class TileArea(val x:Int,val y:Int,val width:Int,val height:Int)
private data class PageTile(val bitmap:Bitmap,val area:TileArea)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    document:OpenDocument,sharing:Boolean,open:()->Unit,back:()->Unit,share:()->Unit,
    position:(Int,Float)->Unit,persist:()->Unit,
) {
    val density=LocalDensity.current
    val list=rememberLazyListState(initialFirstVisibleItemIndex=document.restoredPage)
    val horizontal=rememberScrollState()
    var zoom by remember {mutableFloatStateOf(1f)}
    var savedPage by rememberSaveable {mutableIntStateOf(document.restoredPage)}
    var savedFraction by rememberSaveable {mutableFloatStateOf(document.restoredFraction)}
    var pendingAnchor by remember {mutableStateOf<Anchor?>(Anchor(savedPage,savedFraction))}
    var initialized by remember {mutableStateOf(false)}
    var jump by rememberSaveable {mutableStateOf(false)}
    val sizes=remember {mutableStateMapOf(document.restoredPage to document.restoredSize)}
    val scope=rememberCoroutineScope()
    val current by remember {derivedStateOf {list.firstVisibleItemIndex+1}}
    fun anchor():Anchor {
        val info=list.layoutInfo.visibleItemsInfo.firstOrNull {it.index==list.firstVisibleItemIndex}
        return Anchor(list.firstVisibleItemIndex,if(info!=null && info.size>0) list.firstVisibleItemScrollOffset.toFloat()/info.size else 0f)
    }
    fun setZoom(value:Float) {
        val next=value.coerceIn(1f,4f)
        if(next!=zoom) {pendingAnchor=anchor();zoom=next}
    }
    val latestSetZoom by rememberUpdatedState<(Float)->Unit> {setZoom(it)}
    val latestZoom by rememberUpdatedState(zoom)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title={Text(document.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)},
            navigationIcon={IconButton(onClick=back){Icon(painterResource(R.drawable.ic_arrow_back),"К документам")}},
            actions={
                IconButton(onClick=open) {Icon(painterResource(R.drawable.ic_folder_open),"Открыть другой PDF")}
                IconButton(onClick=share,enabled=!sharing) {
                    if(sharing) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                    else Icon(painterResource(R.drawable.ic_share),"Поделиться PDF")
                }
            })
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest).clip(RoundedCornerShape(0.dp))) {
            val viewportWidthPx=with(density) {maxWidth.roundToPx()}
            val viewportHeightPx=with(density) {maxHeight.roundToPx()}
            val pageWidth=maxWidth*zoom
            val pageWidthPx=with(density) {pageWidth.roundToPx()}
            LaunchedEffect(pageWidthPx) {
                val target=pendingAnchor?:anchor()
                val size=sizes[target.page]?:document.engine.pageSize(target.page).also {sizes[target.page]=it}
                val height=pageWidthPx.toFloat()*size.height/size.width
                list.scrollToItem(target.page,(height*target.fraction.coerceIn(0f,0.999f)).roundToInt())
                if(zoom==1f)horizontal.scrollTo(0)
                pendingAnchor=null
                initialized=true
            }
            LaunchedEffect(list,initialized) {
                if(!initialized)return@LaunchedEffect
                snapshotFlow {anchor()}.distinctUntilChanged().collectLatest { a ->
                    savedPage=a.page;savedFraction=a.fraction
                    position(a.page,a.fraction)
                    delay(400)
                    persist()
                }
            }
            // Preload one neighbour in each direction after scrolling settles.
            LaunchedEffect(current,viewportWidthPx) {
                delay(180)
                for(index in listOf(current-2,current).filter {it in 0 until document.engine.pageCount}) {
                    try {
                        sizes[index]=document.engine.pageSize(index)
                        document.engine.render(index,viewportWidthPx)
                    } catch(e:CancellationException) {throw e} catch(_:Exception) { /* Visible pages display errors. */ }
                }
            }
            Box(Modifier.fillMaxSize().pointerInput(document) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                    do {
                        val event=awaitPointerEvent(PointerEventPass.Initial)
                        if(event.changes.count {it.pressed}>=2) {
                            val factor=event.calculateZoom()
                            if(factor.isFinite() && factor>0f) latestSetZoom(latestZoom*factor)
                            event.changes.forEach {it.consume()}
                        }
                    } while(event.changes.any {it.pressed})
                }
            }.horizontalScroll(horizontal,enabled=zoom>1f)) {
                LazyColumn(state=list,modifier=Modifier.width(pageWidth).fillMaxHeight().testTag("pages"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(document.engine.pageCount,key={it}) {index ->
                        val size=sizes[index]
                        LaunchedEffect(index) {
                            try {sizes[index]=document.engine.pageSize(index)}
                            catch(e:CancellationException){throw e} catch(_:Exception) { }
                        }
                        val ratio=size?.let {it.height.toFloat()/it.width}?:document.restoredSize.let {it.height.toFloat()/it.width}
                        val pageHeightPx=(pageWidthPx*ratio).roundToInt().coerceAtLeast(1)
                        val info=list.layoutInfo.visibleItemsInfo.firstOrNull {it.index==index}
                        val top=(-(info?.offset?:0)).coerceIn(0,pageHeightPx)
                        val visibleHeight=(viewportHeightPx-(info?.offset?:0).coerceAtLeast(0)).coerceIn(0,pageHeightPx-top)
                        val area=if(zoom>1.05f && visibleHeight>0 && size!=null && info!=null) {
                            val x=(horizontal.value/64)*64
                            val y=(top/64)*64
                            TileArea(x,y,(viewportWidthPx+128).coerceAtMost(pageWidthPx-x),(visibleHeight+128).coerceAtMost(pageHeightPx-y))
                        } else null
                        PdfPage(document.engine,index,size,viewportWidthPx,pageWidthPx,pageHeightPx,area,
                            Modifier.width(pageWidth).height(pageWidth*ratio).pointerInput(document) {
                                detectTapGestures(onDoubleTap={latestSetZoom(if(latestZoom>1f)1f else 2f)})
                            })
                    }
                }
            }
        }
        Surface(tonalElevation=2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={jump=true},modifier=Modifier.testTag("page-counter")) {
                    Text("$current / ${document.engine.pageCount}")
                    Spacer(Modifier.width(4.dp));Icon(painterResource(R.drawable.ic_unfold_more),null,Modifier.size(18.dp))
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={setZoom(zoom-0.5f)},enabled=zoom>1f){Icon(painterResource(R.drawable.ic_remove),"Уменьшить")}
                    TextButton(onClick={setZoom(1f)}) {Text("${(zoom*100).roundToInt()}%")}
                    IconButton(onClick={setZoom(zoom+0.5f)},enabled=zoom<4f){Icon(painterResource(R.drawable.ic_add),"Увеличить")}
                }
            }
        }
    }
    if(jump) PageJumpDialog(current,document.engine.pageCount,{jump=false}) {index ->
        scope.launch {list.scrollToItem(index);position(index,0f);persist()}
        jump=false
    }
}

@Composable
private fun PdfPage(engine:PdfEngine,index:Int,size:PageSize?,baseWidth:Int,pageWidth:Int,pageHeight:Int,area:TileArea?,modifier:Modifier) {
    var base by remember(engine,index) {mutableStateOf<Bitmap?>(null)}
    var error by remember(engine,index) {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    var tile by remember(pageWidth,pageHeight) {mutableStateOf<PageTile?>(null)}
    LaunchedEffect(engine,index,baseWidth,retry) {
        try {error=false;base=engine.render(index,baseWidth)}
        catch(e:CancellationException) {throw e} catch(_:Exception) {error=true}
    }
    LaunchedEffect(area,pageWidth,pageHeight,retry) {
        if(area!=null && size!=null && area.width>0 && area.height>0) {
            delay(100)
            try {
                val region=PageRegion(
                    area.x.toFloat()/pageWidth*size.width,area.y.toFloat()/pageHeight*size.height,
                    (area.x+area.width).toFloat()/pageWidth*size.width,(area.y+area.height).toFloat()/pageHeight*size.height)
                tile=PageTile(engine.render(index,area.width,region),area)
            } catch(e:CancellationException) {throw e} catch(_:Exception) { }
        }
    }
    val density=LocalDensity.current
    Box(modifier.background(Color.White).clip(RoundedCornerShape(0.dp))) {
        base?.let {Image(it.asImageBitmap(),"Страница ${index+1}",Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)}
        tile?.let {t ->
            Image(t.bitmap.asImageBitmap(),null,
                Modifier.offset {IntOffset(t.area.x,t.area.y)}.size(with(density){t.area.width.toDp()},with(density){t.area.height.toDp()}),contentScale=ContentScale.FillBounds)
        }
        if(error) Column(Modifier.align(Alignment.Center).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Страница не загрузилась",color=Color.DarkGray)
            TextButton(onClick={retry++}) {Text("Повторить")}
        } else if(base==null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp),strokeWidth=2.dp)
    }
}

@Composable
private fun PageJumpDialog(current:Int,count:Int,dismiss:()->Unit,go:(Int)->Unit) {
    var value by remember {mutableStateOf(current.toString())}
    val index=ReadingRules.pageIndex(value,count)
    AlertDialog(onDismissRequest=dismiss,title={Text("Перейти к странице")},text={
        OutlinedTextField(value=value,onValueChange={value=it},label={Text("Страница от 1 до $count")},singleLine=true,
            isError=index==null,supportingText={if(index==null)Text("Введите число от 1 до $count")},
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Go),
            keyboardActions=KeyboardActions(onGo={index?.let(go)}),modifier=Modifier.testTag("page-number"))
    },confirmButton={TextButton(onClick={index?.let(go)},enabled=index!=null){Text("Перейти")}},
        dismissButton={TextButton(onClick=dismiss){Text("Отмена")}})
}
